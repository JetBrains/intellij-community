package org.jetbrains.plugins.groovy.grape;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.DefaultJavaProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GrabDependencies implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.grape.GrabDependencies");

  @NotNull
  public String getText() {
    return "Grab the artifacts";
  }

  @NotNull
  public String getFamilyName() {
    return "Grab";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final GrAnnotation anno = PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), GrAnnotation.class, false);
    if (anno == null) {
      return false;
    }

    final String qname = anno.getQualifiedName();
    if (qname == null || !(qname.startsWith("groovy.lang.Grab") || "groovy.lang.Grapes".equals(qname))) {
      return false;
    }

    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) {
      return false;
    }

    return file.getOriginalFile().getVirtualFile() != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    assert module != null;

    final VirtualFile vfile = file.getOriginalFile().getVirtualFile();
    assert vfile != null;

    if (JavaPsiFacade.getInstance(project).findClass("org.apache.ivy.core.report.ResolveReport", file.getResolveScope()) == null) {
      Messages.showErrorDialog("Sorry, but IDEA cannot @Grab the dependencies without Ivy. Please add Ivy to your module dependencies and re-run the action.", "Ivy missing");
      return;
    }

    final JavaParameters javaParameters = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
    try {
      //debug
      //javaParameters.getVMParametersList().add("-Xdebug"); javaParameters.getVMParametersList().add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

      final boolean tests = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(vfile);
      javaParameters.configureByModule(module, tests? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
      DefaultGroovyScriptRunner.configureGenericGroovyRunner(javaParameters, module, tests, "org.jetbrains.plugins.groovy.grape.GrapeRunner");

      javaParameters.getProgramParametersList().add("--classpath");
      javaParameters.getProgramParametersList().add(PathUtil.getJarPathForClass(GrapeRunner.class));
      
      javaParameters.getProgramParametersList().add(FileUtil.toSystemDependentName(vfile.getPath()));

    }
    catch (CantRunException e) {
      Messages.showErrorDialog(e.getMessage(), "Can't run Groovyc");
      return;
    }

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assert sdk != null;
    SdkType sdkType = sdk.getSdkType();
    assert sdkType instanceof JavaSdkType;
    final String exePath = ((JavaSdkType)sdkType).getVMExecutablePath(sdk);

    try {
      final GrapeProcessHandler handler = new GrapeProcessHandler(JdkUtil.setupJVMCommandLine(exePath, javaParameters, true), module);
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing @Grab annotations") {
        
        public void run(@NotNull ProgressIndicator indicator) {
          handler.startNotify();
          handler.waitFor();
        }
      });

    }
    catch (ExecutionException e) {
      LOG.error(e);
    }

  }

  public boolean startInWriteAction() {
    return false;
  }

  private static class GrapeProcessHandler extends DefaultJavaProcessHandler {
    private final StringBuilder myStdOut = new StringBuilder();
    private final StringBuilder myStdErr = new StringBuilder();
    private final Module myModule;

    public GrapeProcessHandler(GeneralCommandLine commandLine, Module module) throws ExecutionException {
      super(commandLine);
      myModule = module;
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

    private void addGrapeDependencies(List<VirtualFile> jars) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      final LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
      for (VirtualFile jar : jars) {
        final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar);
        if (jarRoot != null) {
          final String libName = "Grab:" + jar.getName();

          final Library existing = tableModel.getLibraryByName(libName);
          if (existing != null) {
            tableModel.removeLibrary(existing);
          }

          final Library.ModifiableModel libModel = tableModel.createLibrary(libName).getModifiableModel();
          libModel.addRoot(jarRoot, OrderRootType.CLASSES);
          libModel.commit();
        }
      }
      tableModel.commit();
      model.commit();
    }

    @Override
    protected void notifyProcessTerminated(int exitCode) {
      super.notifyProcessTerminated(exitCode);
      final List<VirtualFile> jars = new ArrayList<VirtualFile>();
      for (String line : myStdOut.toString().split("\n")) {
        if (line.startsWith(GrapeRunner.URL_PREFIX)) {
          try {
            final URL url = new URL(line.substring(GrapeRunner.URL_PREFIX.length()));
            final File libFile = new File(url.toURI());
            if (libFile.exists() && libFile.getName().endsWith(".jar")) {
              final VirtualFile vfile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libFile);
              ContainerUtil.addIfNotNull(vfile, jars);
            }
          }
          catch (MalformedURLException e) {
            LOG.error(e);
          }
          catch (URISyntaxException e) {
            LOG.error(e);
          }
        }
      }
      new WriteAction() {
        protected void run(Result result) throws Throwable {
          final String title = jars.size() + " dependencies added";
          final String descr = jars.size() > 0 ? "@Grab completed successfully" : myStdOut.toString().replaceAll("\n", "<br>") + "<p>" + myStdErr.toString().replaceAll("\n", "<br>");
          Notifications.Bus.notify(new Notification("Grape", title, descr, NotificationType.INFORMATION), NotificationDisplayType.BALLOON, myModule.getProject());
          if (!jars.isEmpty()) {
            addGrapeDependencies(jars);
          }
        }
      }.execute();
    }
  }
}
