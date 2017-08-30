/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.framework;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture;
import com.intellij.testGuiFramework.fixtures.WelcomeFrameFixture;
import com.intellij.testGuiFramework.fixtures.newProjectWizard.NewProjectWizardFixture;
import org.fest.swing.core.BasicComponentPrinter;
import org.fest.swing.core.ComponentPrinter;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.image.ScreenshotTaker;
import org.fest.swing.timing.Condition;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.intellij.ide.impl.ProjectUtil.closeAndDispose;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtilRt.delete;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.testGuiFramework.framework.GuiTestUtil.*;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.*;


@RunWith(GuiTestLocalRunner.class)
public abstract class GuiTestBase {
  public Robot myRobot;

  private final Logger LOG = Logger.getInstance(GuiTestBase.class);

  private ScreenshotTaker myScreenshotTaker = new ScreenshotTaker();

  @Rule public final TestWatcher myWatcher = new TestWatcher() {

    @Override
    protected void failed(Throwable e, Description description) {
      String screenshotName = description.getTestClass().getSimpleName() + "." + description.getMethodName();
      takeScreenshotOnFailure(e, screenshotName);
    }
  };

  protected void takeScreenshotOnFailure(Throwable e, String screenshotName) {


    try {
      File file = new File(IdeTestApplication.getFailedTestScreenshotDirPath(), screenshotName + ".png");
      if (file.exists()) {
        String dateAndTime = getDateAndTime();
        file = new File(IdeTestApplication.getFailedTestScreenshotDirPath(), screenshotName + "." + dateAndTime + ".png");
      }
      //noinspection ResultOfMethodCallIgnored
      file.delete();
      if (e instanceof ComponentLookupException)
        LOG.error(getHierarchy() + "\n" + "caused by:", e);
      myScreenshotTaker.saveDesktopAsPng(file.getPath());
      LOG.info("Screenshot: " + file);
    }
    catch (Throwable t) {
      LOG.error("Screenshot failed. " + t.getMessage());
    }
  }

  protected IdeFrameFixture myProjectFrame;

  /**
   * @return the name of the test class being executed.
   */
  protected String getTestName() {
    return this.getClass().getSimpleName();
  }


  public void setRobot(Robot robot) {
    myRobot = robot;
  }

  public GuiTestBase() {

  }

  public GuiTestBase(Robot robot) {
    myRobot = robot;
  }

  @Before
  public void setUp() throws Exception {
    //if test is local -> create control test and run it inside
    Application application = ApplicationManager.getApplication();
    assertNotNull(application); // verify that we are using the IDE's ClassLoader.
  }

  @After
  public void tearDown() throws InvocationTargetException, InterruptedException {
    failIfIdeHasFatalErrors();
    if (myProjectFrame != null) {
      DumbService.getInstance(myProjectFrame.getProject()).repeatUntilPassesInSmartMode(
        () -> myProjectFrame.waitForBackgroundTasksToFinish());
      myProjectFrame = null;
    }
    if (myRobot != null) {
      myRobot.cleanUpWithoutDisposingWindows();
      // We close all modal dialogs left over, because they block the AWT thread and could trigger a deadlock in the next test.
      for (Window window : Window.getWindows()) {
        if (window.isShowing() && window instanceof Dialog) {
          if (((Dialog)window).getModalityType() == Dialog.ModalityType.APPLICATION_MODAL) {
            myRobot.close(window);
            fail("Modal dialog still active: " + window);
          }
        }
      }
      myRobot = null;
    }
  }

  @NotNull
  protected WelcomeFrameFixture findWelcomeFrame() {
    return WelcomeFrameFixture.find(myRobot);
  }

  @NotNull
  protected NewProjectWizardFixture findNewProjectWizard() {
    return NewProjectWizardFixture.find(myRobot);
  }

  @NotNull
  protected IdeFrameFixture findIdeFrame(@NotNull String projectName, @NotNull File projectPath) {
    return IdeFrameFixture.find(myRobot, projectPath, projectName);
  }

  @SuppressWarnings("UnusedDeclaration")
  // Called by GuiTestRunner via reflection.
  protected void closeAllProjects() {
    pause(new Condition("Close all projects") {
      @Override
      public boolean test() {
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
              for (Project project : openProjects) {
                assertTrue("Failed to close project " + quote(project.getName()), closeAndDispose(project));
              }
            });
          }
        });
        return ProjectManager.getInstance().getOpenProjects().length == 0;
      }
    }, SHORT_TIMEOUT);

    //noinspection ConstantConditions
    boolean welcomeFrameShown = execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
          WelcomeFrame.showNow();

          WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
          windowManager.disposeRootFrame();
          return true;
        }
        return false;
      }
    });

    if (welcomeFrameShown) {
      pause(new Condition("'Welcome' frame to show up") {
        @Override
        public boolean test() {
          for (Frame frame : Frame.getFrames()) {
            if (frame == WelcomeFrame.getInstance() && frame.isShowing()) {
              return true;
            }
          }
          return false;
        }
      }, SHORT_TIMEOUT);
    }
  }

  @NotNull
  protected IdeFrameFixture importSimpleProject() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("SimpleProject");
  }

  @NotNull
  protected IdeFrameFixture importMultiModule() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("MultiModule");
  }

  @NotNull
  protected IdeFrameFixture importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    return importProjectAndWaitForProjectSyncToFinish(projectDirName, null);
  }

  @NotNull
  protected IdeFrameFixture importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName, @Nullable String gradleVersion)
    throws IOException {
    File projectPath = setUpProject(projectDirName, false);
    VirtualFile toSelect = findFileByIoFile(projectPath, false);
    assertNotNull(toSelect);

    doImportProject(toSelect);

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    //TODO: add wait to open project

    return projectFrame;
  }

  @NotNull
  protected File importProject(@NotNull String projectDirName) throws IOException {
    File projectPath = setUpProject(projectDirName, false);
    VirtualFile toSelect = findFileByIoFile(projectPath, false);
    assertNotNull(toSelect);
    doImportProject(toSelect);
    return projectPath;
  }

  private static void doImportProject(@NotNull final VirtualFile projectDir) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
          ProjectUtil.openOrImport(projectDir.getPath(), null, false);
        });
      }
    });
  }


  @NotNull
  private File setUpProject(@NotNull String projectDirName,
                            boolean forOpen) throws IOException {
    File projectPath = copyProjectBeforeOpening(projectDirName);
    assertNotNull(projectPath);

    return projectPath;
  }


  @NotNull
  protected File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    File projectPath = getTestProjectDirPath(projectDirName);
    if (projectPath.isDirectory()) {
      delete(projectPath);
      System.out.println(String.format("Deleted project path '%1$s'", projectPath.getPath()));
    }
    copyDir(masterProjectPath, projectPath);
    System.out.println(String.format("Copied project '%1$s' to path '%2$s'", projectDirName, projectPath.getPath()));
    return projectPath;
  }


  @NotNull
  protected File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(getTestProjectsRootDirPath(), projectDirName);
  }

  @NotNull
  protected File getTestProjectDirPath(@NotNull String projectDirName) {
    return new File(getProjectCreationDirPath(), projectDirName);
  }

  protected void cleanUpProjectForImport(@NotNull File projectPath) {
    File dotIdeaFolderPath = new File(projectPath, Project.DIRECTORY_STORE_FOLDER);
    if (dotIdeaFolderPath.isDirectory()) {
      File modulesXmlFilePath = new File(dotIdeaFolderPath, "modules.xml");
      if (modulesXmlFilePath.isFile()) {
        SAXBuilder saxBuilder = new SAXBuilder();
        try {
          Document document = saxBuilder.build(modulesXmlFilePath);
          XPath xpath = XPath.newInstance("//*[@fileurl]");
          //noinspection unchecked
          List<Element> modules = xpath.selectNodes(document);
          int urlPrefixSize = "file://$PROJECT_DIR$/".length();
          for (Element module : modules) {
            String fileUrl = module.getAttributeValue("" +
                                                      "fileurl");
            if (!StringUtil.isEmpty(fileUrl)) {
              String relativePath = toSystemDependentName(fileUrl.substring(urlPrefixSize));
              File imlFilePath = new File(projectPath, relativePath);
              if (imlFilePath.isFile()) {
                delete(imlFilePath);
              }
              // It is likely that each module has a "build" folder. Delete it as well.
              File buildFilePath = new File(imlFilePath.getParentFile(), "build");
              if (buildFilePath.isDirectory()) {
                delete(buildFilePath);
              }
            }
          }
        }
        catch (Throwable ignored) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }
      }
      delete(dotIdeaFolderPath);
    }
  }

  public static String getHierarchy() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(out, true);
    ComponentPrinter componentPrinter = BasicComponentPrinter.printerWithCurrentAwtHierarchy();
    componentPrinter.printComponents(printStream);
    printStream.flush();
    return new String(out.toByteArray());
  }

  private static String getDateAndTime() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd.HH_mm_ss_SSS");
    Date date = new Date();
    return dateFormat.format(date); //2016/11/16 12:08:43
  }

  @NotNull
  protected IdeFrameFixture findIdeFrame(@NotNull File projectPath) {
    return IdeFrameFixture.find(myRobot, projectPath, null);
  }


  protected IdeFrameFixture findIdeFrame() {
    return IdeFrameFixture.find(myRobot, null, null);
  }
}