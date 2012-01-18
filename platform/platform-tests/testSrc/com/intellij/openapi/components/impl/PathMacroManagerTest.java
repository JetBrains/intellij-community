package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.mock.MockFileSystem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFileSystem;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.picocontainer.PicoContainer;

import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;

/**
 * @author mike
 */
@RunWith(JMock.class)
public class PathMacroManagerTest {
  private static final String APP_HOME = PathManager.getHomePath();
  private static final String USER_HOME = StringUtil.trimEnd(SystemProperties.getUserHome(), "/");
  private Module myModule;
  private ProjectEx myProject;
  private PathMacrosImpl myPathMacros;
  public Mockery context = new JUnit4Mockery();
  {
    context.setImposteriser(ClassImposteriser.INSTANCE);
  }

  protected ApplicationEx myApplication;
  private IFileSystem myOldFileSystem;
  protected MockFileSystem myFileSystem;
  protected PicoContainer myAppPico;
  private Disposable myDisposable = Disposer.newDisposable();

  @Before
  public final void setupApplication() throws Exception {
    myApplication = mock(ApplicationEx.class, "application");
    myAppPico = mock(PicoContainer.class);

    context.checking(new Expectations() {
      {
        allowing(myApplication).isUnitTestMode(); will(returnValue(false));
        allowing(myApplication).getName(); will(returnValue("IDEA"));
        allowing(myApplication).getPicoContainer(); will(returnValue(myAppPico));

        //some tests leave invokeLaters after them...
        allowing(myApplication).invokeLater(with(any(Runnable.class)), with(any(ModalityState.class)));

        allowing(myApplication).runReadAction(with(any(Runnable.class)));
        will(new Action() {
          @Override
          public void describeTo(final Description description) {
            description.appendText("runs runnable");
          }

          @Override
          public Object invoke(final Invocation invocation) throws Throwable {
            ((Runnable)invocation.getParameter(0)).run();
            return null;
          }
        });
      }
    });

    ApplicationManager.setApplication(myApplication, myDisposable);
  }

  @Before
  public final void setupFileSystem() throws Exception {
    myOldFileSystem = FileSystem.FILE_SYSTEM;
    myFileSystem = new MockFileSystem();
    FileSystem.FILE_SYSTEM = myFileSystem;
  }

  @After
  public final void restoreApplication() throws Exception {
    Disposer.dispose(myDisposable);
    myDisposable = null;
  }

  @After
  public final void restoreFilesystem() throws Exception {
    FileSystem.FILE_SYSTEM = myOldFileSystem;
  }

  protected <T> T mock(Class<T> typeToMock) {
    return context.mock(typeToMock);
  }

  protected <T> T mock(Class<T> typeToMock, String name) {
    return context.mock(typeToMock, name);
  }


  private void setUpMocks(final String projectPath) {
    myModule = context.mock(Module.class);
    myPathMacros = context.mock(PathMacrosImpl.class);

    myProject = context.mock(ProjectEx.class);
    final VirtualFile file = context.mock(VirtualFile.class);
    final VirtualFile parentFile = context.mock(VirtualFile.class);

    context.checking(new Expectations() {{
      allowing(myModule).isDisposed(); will(returnValue(false));
      allowing(myProject).isDisposed(); will(returnValue(false));
      allowing(myModule).getModuleFilePath(); will(returnValue(projectPath + "/module/module.iml"));
      allowing(myProject).getProjectFilePath(); will(returnValue(projectPath));

      allowing(myApplication).getComponent(with(equal(PathMacros.class))); will(returnValue(myPathMacros));

      allowing(myPathMacros).addMacroReplacements(with(any(ReplacePathToMacroMap.class)));

      allowing(file).getPath();

      will(returnValue(projectPath));
      allowing(file).getParent(); will(returnValue(parentFile));
      allowing(parentFile).getPath(); will(returnValue(StringUtil.getPackageName(projectPath, '/')));
      allowing(myProject).getBaseDir(); will(returnValue(file));
      allowing(myModule).getProject(); will(returnValue(myProject));
    }});
  }


  @Test
  public void testRightMacrosOrder_RelativeValues_NoVariables() throws Exception {
    assumeThat(SystemInfo.isWindows, is(false));

    setUpMocks("/tmp/foo");

    final ReplacePathToMacroMap replacePathMap = new ModulePathMacroManager(myModule).getReplacePathMap();
    final String s = mapToString(replacePathMap);
    assertEquals("file:/tmp/foo/module -> file:$MODULE_DIR$\n" +
                 "file://tmp/foo/module -> file:/$MODULE_DIR$\n" +
                 "file:///tmp/foo/module -> file://$MODULE_DIR$\n" +
                 "jar:/tmp/foo/module -> jar:$MODULE_DIR$\n" +
                 "jar://tmp/foo/module -> jar:/$MODULE_DIR$\n" +
                 "jar:///tmp/foo/module -> jar://$MODULE_DIR$\n" +
                 "/tmp/foo/module -> $MODULE_DIR$\n" +
                 APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
                 "file:" + APP_HOME + " -> file:$APPLICATION_HOME_DIR$\n" +
                 "file:/" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "file://" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "jar:" + APP_HOME + " -> jar:$APPLICATION_HOME_DIR$\n" +
                 "jar:/" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 "jar://" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 USER_HOME + " -> $USER_HOME$\n" +
                 "file:" + USER_HOME + " -> file:$USER_HOME$\n" +
                 "file:/" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "file://" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "jar:" + USER_HOME + " -> jar:$USER_HOME$\n" +
                 "jar:/" + USER_HOME + " -> jar://$USER_HOME$\n" +
                 "jar://" + USER_HOME + " -> jar://$USER_HOME$\n" +
                 "file:/tmp/foo -> file:$MODULE_DIR$/..\n" +
                 "file://tmp/foo -> file:/$MODULE_DIR$/..\n" +
                 "file:///tmp/foo -> file://$MODULE_DIR$/..\n" +
                 "jar:/tmp/foo -> jar:$MODULE_DIR$/..\n" +
                 "jar://tmp/foo -> jar:/$MODULE_DIR$/..\n" +
                 "jar:///tmp/foo -> jar://$MODULE_DIR$/..\n" +
                 "/tmp/foo -> $MODULE_DIR$/..\n" +
                 "file:/tmp -> file:$MODULE_DIR$/../..\n" +
                 "file://tmp -> file:/$MODULE_DIR$/../..\n" +
                 "file:///tmp -> file://$MODULE_DIR$/../..\n" +
                 "jar:/tmp -> jar:$MODULE_DIR$/../..\n" +
                 "jar://tmp -> jar:/$MODULE_DIR$/../..\n" +
                 "jar:///tmp -> jar://$MODULE_DIR$/../..\n" +
                 "/tmp -> $MODULE_DIR$/../..", s);
  }

  @Test
  public void testPathsOutsideProject() throws Exception {
    assumeThat(SystemInfo.isWindows, is(false));

    setUpMocks("/tmp/foo");

    final ReplacePathToMacroMap replacePathMap = new ProjectPathMacroManager(myProject).getReplacePathMap();
    final String s = mapToString(replacePathMap);
    assertEquals("file:/tmp/foo -> file:$PROJECT_DIR$\n" +
                 "file://tmp/foo -> file:/$PROJECT_DIR$\n" +
                 "file:///tmp/foo -> file://$PROJECT_DIR$\n" +
                 "jar:/tmp/foo -> jar:$PROJECT_DIR$\n" +
                 "jar://tmp/foo -> jar:/$PROJECT_DIR$\n" +
                 "jar:///tmp/foo -> jar://$PROJECT_DIR$\n" +
                 "/tmp/foo -> $PROJECT_DIR$\n" +
                 APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
                 "file:" + APP_HOME + " -> file:$APPLICATION_HOME_DIR$\n" +
                 "file:/" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "file://" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "jar:" + APP_HOME + " -> jar:$APPLICATION_HOME_DIR$\n" +
                 "jar:/" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 "jar://" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 USER_HOME + " -> $USER_HOME$\n" +
                 "file:" + USER_HOME + " -> file:$USER_HOME$\n" +
                 "file:/" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "file://" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "jar:" + USER_HOME + " -> jar:$USER_HOME$\n" +
                 "jar:/" + USER_HOME + " -> jar://$USER_HOME$\n" +
                 "jar://" + USER_HOME + " -> jar://$USER_HOME$\n" +
                 "file:/tmp -> file:$PROJECT_DIR$/..\n" +
                 "file://tmp -> file:/$PROJECT_DIR$/..\n" +
                 "file:///tmp -> file://$PROJECT_DIR$/..\n" +
                 "jar:/tmp -> jar:$PROJECT_DIR$/..\n" +
                 "jar://tmp -> jar:/$PROJECT_DIR$/..\n" +
                 "jar:///tmp -> jar://$PROJECT_DIR$/..\n" +
                 "/tmp -> $PROJECT_DIR$/..", s);
  }

  @Test
  public void testProjectUnderUserHome() throws Exception {
    assumeThat(SystemInfo.isWindows, is(false));

    setUpMocks(USER_HOME + "/IdeaProjects/foo");

    final ReplacePathToMacroMap replacePathMap = new ModulePathMacroManager(myModule).getReplacePathMap();
    final String s = mapToString(replacePathMap);
    assertEquals("file:" + USER_HOME + "/IdeaProjects/foo/module -> file:$MODULE_DIR$\n" +
                 "file:/" + USER_HOME + "/IdeaProjects/foo/module -> file:/$MODULE_DIR$\n" +
                 "file://" + USER_HOME + "/IdeaProjects/foo/module -> file://$MODULE_DIR$\n" +
                 "jar:" + USER_HOME + "/IdeaProjects/foo/module -> jar:$MODULE_DIR$\n" +
                 "jar:/" + USER_HOME + "/IdeaProjects/foo/module -> jar:/$MODULE_DIR$\n" +
                 "jar://" + USER_HOME + "/IdeaProjects/foo/module -> jar://$MODULE_DIR$\n" +
                 USER_HOME + "/IdeaProjects/foo/module -> $MODULE_DIR$\n" +
                 APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
                 "file:" + APP_HOME + " -> file:$APPLICATION_HOME_DIR$\n" +
                 "file:/" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "file://" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "jar:" + APP_HOME + " -> jar:$APPLICATION_HOME_DIR$\n" +
                 "jar:/" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 "jar://" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 "file:" + USER_HOME + "/IdeaProjects/foo -> file:$MODULE_DIR$/..\n" +
                 "file:/" + USER_HOME + "/IdeaProjects/foo -> file:/$MODULE_DIR$/..\n" +
                 "file://" + USER_HOME + "/IdeaProjects/foo -> file://$MODULE_DIR$/..\n" +
                 "jar:" + USER_HOME + "/IdeaProjects/foo -> jar:$MODULE_DIR$/..\n" +
                 "jar:/" + USER_HOME + "/IdeaProjects/foo -> jar:/$MODULE_DIR$/..\n" +
                 "jar://" + USER_HOME + "/IdeaProjects/foo -> jar://$MODULE_DIR$/..\n" +
                 USER_HOME + "/IdeaProjects/foo -> $MODULE_DIR$/..\n" +
                 "file:" + USER_HOME + "/IdeaProjects -> file:$MODULE_DIR$/../..\n" +
                 "file:/" + USER_HOME + "/IdeaProjects -> file:/$MODULE_DIR$/../..\n" +
                 "file://" + USER_HOME + "/IdeaProjects -> file://$MODULE_DIR$/../..\n" +
                 "jar:" + USER_HOME + "/IdeaProjects -> jar:$MODULE_DIR$/../..\n" +
                 "jar:/" + USER_HOME + "/IdeaProjects -> jar:/$MODULE_DIR$/../..\n" +
                 "jar://" + USER_HOME + "/IdeaProjects -> jar://$MODULE_DIR$/../..\n" +
                 USER_HOME + "/IdeaProjects -> $MODULE_DIR$/../..\n" +
                 USER_HOME + " -> $USER_HOME$\n" +
                 "file:" + USER_HOME + " -> file:$USER_HOME$\n" +
                 "file:/" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "file://" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "jar:" + USER_HOME + " -> jar:$USER_HOME$\n" +
                 "jar:/" + USER_HOME + " -> jar://$USER_HOME$\n" +
                 "jar://" + USER_HOME + " -> jar://$USER_HOME$", s);
  }

  private static String mapToString(final ReplacePathToMacroMap replacePathMap) {
    StringBuilder buf = new StringBuilder();


    final List<String> pathIndex = replacePathMap.getPathIndex();
    for (String s : pathIndex) {
      if (buf.length() > 0) buf.append("\n");
      buf.append(s);
      buf.append(" -> ");
      buf.append(replacePathMap.get(s));
    }

    return buf.toString();
  }
}
