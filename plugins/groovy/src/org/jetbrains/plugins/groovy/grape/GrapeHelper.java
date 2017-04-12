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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrapeHelper {
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Grape", NotificationDisplayType.BALLOON, true);
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.grape.GrapeRunner");
  public static final String GRAPE_RUNNER = "org.jetbrains.plugins.groovy.grape.GrapeRunner";


  public static void processGrabs(Project project, SearchScope scope, ResultHandler resultHandler) {

    if (JavaPsiFacade.getInstance(project).findClass("org.apache.ivy.core.report.ResolveReport", GlobalSearchScope.allScope(project)) ==
        null) {
      Messages
        .showErrorDialog(
          "Sorry, but IDEA cannot @Grab the dependencies without Ivy. Please add Ivy to your module dependencies and re-run the action.",
          "Ivy Missing");
      return;
    }
    runDownload(project, findGrabAnnotations(project, scope), resultHandler);
  }

  public static void runDownload(Project project, List<PsiAnnotation> annotations, ResultHandler resultHandler) {
    Map<String, GeneralCommandLine> commandLines = new HashMap<>();
    try {
      for (PsiAnnotation annotation : annotations) {
        String grabQuery = grabQuery(annotation);
        commandLines.put(grabQuery, getDownloadCommandLine(annotation));
      }
    } catch (CantRunException e) {
      String title = "Can't run @Grab: " + ExceptionUtil.getMessage(e);
      NOTIFICATION_GROUP.createNotification(title, ExceptionUtil.getThrowableText(e), NotificationType.ERROR, null).notify(project);
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Downloading @Grab Annotation") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          commandLines.forEach((grabQuery, commandLine) -> {
            try {
              indicator.setText2(grabQuery);
              final GrapeProcessHandler handler = new GrapeProcessHandler(commandLine);
              handler.startNotify();
              handler.waitFor();
              resultHandler.accept(grabQuery, handler);
            }
            catch (ExecutionException e) {
              LOG.error(e);
            }
          });
        } finally {
          resultHandler.finish();
        }
      }
    });
  }

  public static GeneralCommandLine getDownloadCommandLine(PsiAnnotation annotation) throws CantRunException {
    Module module = ModuleUtilCore.findModuleForPsiElement(annotation);
    assert module != null;
    final JavaParameters javaParameters = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);

    DefaultGroovyScriptRunner.configureGenericGroovyRunner(javaParameters, module, GRAPE_RUNNER, false, true);
    javaParameters.getClassPath().add(PathUtil.getJarPathForClass(GrapeRunner.class));
    javaParameters.getProgramParametersList().add(grabQuery(annotation));
    javaParameters.setUseDynamicClasspath(true);
    return javaParameters.toCommandLine();
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
      StringBuilder messages = new StringBuilder(jarCount + " jar");
      if (jarCount != 1) {
        messages.append("s");
      }
      messages.append(": ");
      for (VirtualFile file : jarFiles) {
        messages.append(file.getCanonicalPath()).append("; ");
      }
      if (jarCount == 0) {
        messages.append("<br>").append(myStdOut.toString().replaceAll("\n", "<br>")).append("<p>")
          .append(myStdErr.toString().replaceAll("\n", "<br>"));
      }
      return messages.toString();
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
    void accept(@NotNull String grabText, @NotNull GrapeProcessHandler handler);
    void finish();
  }
}