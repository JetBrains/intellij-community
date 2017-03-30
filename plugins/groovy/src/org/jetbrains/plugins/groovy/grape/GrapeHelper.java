/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.grape;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class GrapeHelper {
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Grape", NotificationDisplayType.BALLOON, true);
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.grape.GrapeRunner");
  public static final String GRAPE_RUNNER = "org.jetbrains.plugins.groovy.grape.GrapeRunner";

  public static void processProject(Project project) {
    GrabService grabService = GrabService.getInstance(project);
    grabService.clearState();
    processGrabs(project, GlobalSearchScope.allScope(project), (grabText, handler) -> {
      if (handler.getJarFiles().size() != 0)
        grabService.addDependencies(grabText, handler.getJarFiles().stream().map(VirtualFile::getPath).collect(Collectors.toList()));
    });
  }
  public static void processGrabs(Project project, SearchScope scope, ResultHandler resultHandler) {

    if (JavaPsiFacade.getInstance(project).findClass("org.apache.ivy.core.report.ResolveReport", GlobalSearchScope.allScope(project)) ==
        null) {
      Messages
        .showErrorDialog(
          "Sorry, but IDEA cannot @Grab the dependencies without Ivy. Please add Ivy to your module dependencies and re-run the action.",
          "Ivy Missing");
      return;
    }

    findGrabAnnotations(project, scope).forEach(annotation -> {
      try {
        run(annotation, resultHandler);
      }
      catch (CantRunException e) {
        String title = "Can't run @Grab: " + ExceptionUtil.getMessage(e);
        NOTIFICATION_GROUP.createNotification(title, ExceptionUtil.getThrowableText(e), NotificationType.ERROR, null).notify(project);
      }
    });
  }

  public static void run(PsiAnnotation annotation, ResultHandler resultHandler) throws CantRunException {
    final Module module = ModuleUtilCore.findModuleForPsiElement(annotation);
    assert module != null;
    String grabQuery = grabQuery(annotation);

    final JavaParameters javaParameters = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);

    DefaultGroovyScriptRunner.configureGenericGroovyRunner(javaParameters, module, GRAPE_RUNNER, false, true);
    javaParameters.getClassPath().add(PathUtil.getJarPathForClass(GrapeRunner.class));
    javaParameters.getProgramParametersList().add(grabQuery);
    javaParameters.setUseDynamicClasspath(true);
    GeneralCommandLine commandLine = javaParameters.toCommandLine();

    ProgressManager.getInstance().run(new Task.Backgroundable(annotation.getProject(), "Processing @Grab Annotation") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {

        indicator.setText2(grabQuery);
        try {
          final GrapeProcessHandler handler = new GrapeProcessHandler(commandLine);
          handler.startNotify();
          handler.waitFor();
          resultHandler.accept(grabQuery, handler);
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
      }
    });
  }

  public static String grabQuery(@NotNull PsiAnnotation anno) {
    String qname = anno.getQualifiedName();
    if (GrabAnnos.GRAB_ANNO.equals(qname)) {
      return anno.getText() + " " + grabCommonPart(anno.getContainingFile());
    }
    return "";
  }

  public static String grabCommonPart(PsiFile file) {
    if (file == null) return "";
    StringBuilder builder = new StringBuilder();
    file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof GrAnnotation) {
          GrAnnotation anno = (GrAnnotation)element;
          String qname = anno.getQualifiedName();
          if (GrabAnnos.GRAB_EXCLUDE_ANNO.equals(qname) || GrabAnnos.GRAB_RESOLVER_ANNO.equals(qname)) {
            builder.append(anno.getText()).append(" ");
          }
        }
        super.visitElement(element);
      }
    });
    return builder.toString().trim();
  }

  public static class GrapeProcessHandler extends OSProcessHandler {
    private final StringBuilder myStdOut = new StringBuilder();
    private final StringBuilder myStdErr = new StringBuilder();

    private final List<VirtualFile> jarFiles = new ArrayList<>();

    public GrapeProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
      super(commandLine);
    }

    public List<VirtualFile> getJarFiles() {
      return jarFiles;
    }

    @Override
    public void notifyTextAvailable(String text, Key outputType) {
      text = StringUtil.convertLineSeparators(text);
      if (LOG.isDebugEnabled()) {
        LOG.debug(outputType + text);
      }
      if (outputType == ProcessOutputTypes.STDOUT) {
        myStdOut.append(text);
      }
      else if (outputType == ProcessOutputTypes.STDERR) {
        myStdErr.append(text);
      }
    }


    @Override
    protected void notifyProcessTerminated(int exitCode) {
      try {

        for (String line : myStdOut.toString().split("\n")) {
          if (line.startsWith(GrapeRunner.URL_PREFIX)) {
            try {
              final URL url = new URL(line.substring(GrapeRunner.URL_PREFIX.length()));
              final File libFile = new File(url.toURI());
              if (libFile.exists() && libFile.getName().endsWith(".jar")) {
                ContainerUtil.addIfNotNull(jarFiles, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libFile));
              }
            }
            catch (MalformedURLException | URISyntaxException e) {
              LOG.error(e);
            }
          }
        }
      }
      finally {
        super.notifyProcessTerminated(exitCode);
      }
    }

    public String getMessages() {
      int jarCount = jarFiles.size();
      String messages = jarCount + " jar";
      if (jarCount != 1) {
        messages += "s";
      }
      if (jarCount == 0) {
        messages += "<br>" + myStdOut.toString().replaceAll("\n", "<br>") + "<p>" + myStdErr.toString().replaceAll("\n", "<br>");
      }
      return messages;
    }
  }

  public static List<PsiAnnotation> findGrabAnnotations(@NotNull Project project, @NotNull SearchScope scope) {
    List<PsiAnnotation> annotations = new ArrayList<>();
    PsiClass grab = JavaPsiFacade.getInstance(project).findClass(GrabAnnos.GRAB_ANNO, GlobalSearchScope.allScope(project));
    if (grab != null) {
      ReferencesSearch.search(grab, scope).forEach(reference -> {
        if (reference instanceof GrCodeReferenceElement) {
          PsiElement parent = ((GrCodeReferenceElement)reference).getParent();
          if (parent instanceof PsiAnnotation) {
            annotations.add((PsiAnnotation)parent);
          }
        }
        return true;
      });
    }
    return annotations;
  }

  interface ResultHandler {
    void accept(String grabText, GrapeProcessHandler handler);
  }
}