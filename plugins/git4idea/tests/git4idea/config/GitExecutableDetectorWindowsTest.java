package git4idea.config;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

public class GitExecutableDetectorWindowsTest {
  @Before
  public void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
    Assume.assumeTrue(SystemInfo.isWindows);
    testRoot = FileUtil.createTempDirectory("", "");
    setWindowsRoot(new File(testRoot, "C_"));
  }

  @Test
  public void Simple_case() {
    fs("C:/Program Files/Git/bin/git.exe");
    assertExecutable("C:/Program Files/Git/bin/git.exe");
  }

  @Test public void Prefer_default_Git_without_version_to_versioned_ones() {
    fs("C:/Program Files/Git/bin/git.exe", "C:/Program Files/Git 1.8/bin/git.exe", "C:/Program Files/Git_1.7.1/bin/git.exe");
    assertExecutable("C:/Program Files/Git/bin/git.exe");
  }

  @Test
  public void Prefer_the_latest_version() {
    fs("C:/Program Files (x86)/Git 1.8/cmd/git.exe", "C:/Program Files/Git_1.7.1/bin/git.exe", "C:/Program Files/Git_1.7.5/cmd/git.cmd",
       "C:/Program Files (x86)/Git_1.7.0.2/bin/git.exe");
    assertExecutable("C:/Program Files (x86)/Git 1.8/cmd/git.exe");
  }

  @Test
  public void Prefer_Program_Files_over_x86() {
    fs("C:/Program Files (x86)/Git 1.8/bin/git.exe", "C:/Program Files/Git 1.8/bin/git.exe");
    assertExecutable("C:/Program Files/Git 1.8/bin/git.exe");
  }

  @Test
  public void Prefer_git_cmd_over_git_exe() {
    fs("C:/Program Files (x86)/Git 1.7.4/bin/git.exe", "C:/Program Files/Git 1.7.4/cmd/git.cmd");
    assertExecutable("C:/Program Files/Git 1.7.4/cmd/git.cmd");
  }

  @Test
  public void Prefer_cmd_over_bin_in_newer_versions_of_Git() {
    fs("C:/Program Files (x86)/Git 1.8/bin/git.exe", "C:/Program Files/Git 1.8/cmd/git.exe");
    assertExecutable("C:/Program Files/Git 1.8/cmd/git.exe");
  }

  @Test
  public void _1_8_0_Prefer_cmd_git_cmd_over_cmd_git_exe_and_bin_git_exe() {
    fs("C:/Program Files (x86)/Git_1.8/bin/git.exe", "C:/Program Files (x86)/Git_1.8/cmd/git.cmd",
       "C:/Program Files (x86)/Git_1.8/cmd/git.exe");
    assertExecutable("C:/Program Files (x86)/Git_1.8/cmd/git.cmd");
  }

  @Test
  public void Prefer_msys_over_cygwin() {
    fs("C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe", "C:/Program Files (x86)/Git_1.8.0.2/bin/git.exe", "C:/cygwin/bin/git.exe");
    assertExecutable("C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe");
  }

  @Test
  public void Only_cygwin() {
    fs("C:/cygwin/bin/git.exe");
    assertExecutable("C:/cygwin/bin/git.exe");
  }

  @Test
  public void Many_different_versions_real_case() {
    fs("C:/Program Files (x86)/Git_1.7.0.2/bin/git.exe", "C:/Program Files (x86)/Git_1.7.0.2/cmd/git.cmd",
       "C:/Program Files (x86)/Git_1.7.8/bin/git.exe", "C:/Program Files (x86)/Git_1.7.8/cmd/git.cmd",
       "C:/Program Files (x86)/Git_1.8/bin/git.exe", "C:/Program Files (x86)/Git_1.8/cmd/git.cmd",
       "C:/Program Files (x86)/Git_1.8/cmd/git.exe", "C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe",
       "C:/Program Files (x86)/Git_1.8.0.2/bin/git.exe", "C:/cygwin/bin/git.exe");
    assertExecutable("C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe");
  }

  @Test
  public void Program_not_found_try_git_exe() {
    CAN_RUN = new ArrayList<>(Arrays.asList("git.exe"));
    assertExecutable("git.exe");
  }

  @Test
  public void For_both_git_exe_and_git_cmd_prefer_git_cmd() {
    CAN_RUN = new ArrayList<>(Arrays.asList("git.exe", "git.cmd"));
    assertExecutable("git.cmd");
  }

  @Test
  public void Find_Git_in_PATH() {
    PATH = "D:/Program Files (x86)/Git_distr/cmd";
    fs("D:/Program Files (x86)/Git_distr/cmd/git.cmd");
    assertExecutable("D:/Program Files (x86)/Git_distr/cmd/git.cmd");
  }

  @Test
  public void Find_Git_in_PATH_not_on_the_first_place() {
    PATH = "C:/Ruby193/bin;C:/Users/John.Doe/Documents/Git_1.8.0.2/bin";
    fs("C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe");
    assertExecutable("C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe");
  }

  @Test
  public void No_Git_in_PATH_then_look_in_Program_Files() {
    PATH = "%SystemRoot%/system32;%SystemRoot%;%SystemRoot%/System32/Wbem;%SYSTEMROOT%/System32/WindowsPowerShell/v1.0/;" +
           "C:/Program Files/Intel/DMIX;C:/Program Files/Mercurial/;C:/Program Files/TortoiseHg/;";
    fs("C:/Program Files/Git/bin/git.exe");
    assertExecutable("C:/Program Files/Git/bin/git.exe");
  }

  @Test
  public void Prefer_PATH_to_Program_Files() {
    PATH = "C:/Ruby193/bin;C:/Users/John.Doe/Documents/Git_1.8.0.2/bin";
    fs("C:/Program Files/Git/bin/git.exe", "C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe");
    assertExecutable("C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe");
  }

  @Test
  public void Prefer_the_first_entry_from_the_PATH() {
    PATH = "C:/Ruby193/bin;D:/Git/cmd;C:/Users/John.Doe/Documents/Git_1.8.0.2/bin;";
    fs("D:/Git/cmd/git.cmd", "C:/Users/John.Doe/Documents/Git_1.8.0.2/bin/git.exe");
    assertExecutable("D:/Git/cmd/git.cmd");
  }

  @Test
  public void Dont_use_Git_from_PATH_if_it_doesnt_exist_on_disk() {
    PATH = "C:/Ruby193/bin;D:/Git/cmd;";
    fs("C:/Program Files/Git/bin/git.exe");
    assertExecutable("C:/Program Files/Git/bin/git.exe");
  }

  public void assertExecutable(String expected) {
    // we want to specify unix-like paths in "expected", because they are easier to write :)
    String detected = detect();
    detected = convertBack(detected);
    Assert.assertEquals("Incorrect executable detected", expected, detected);
  }

  public String convertBack(String detected) {
    detected = detected.replace(testRoot.getPath() + File.separator, "").replace("\\", "/");
    detected = returnDiskColon(detected);
    return detected;
  }

  public void fs(String... paths) {
    for (String path : paths) {
      mkPath(path);
    }
  }

  public boolean mkPath(String path) {
    File file = new File(convertPath(path));
    file.getParentFile().mkdirs();
    try {
      return file.createNewFile();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String convertPath(@NotNull String path) {
    path = FileUtil.toSystemDependentName(replaceDiskColon(path));
    return testRoot.getPath() + File.separator + path;
  }

  public static String replaceDiskColon(String path) {
    if (path.charAt(1) == ':') {
      return path.charAt(0) + "_" + path.substring(2);
    }
    return path;
  }

  private static String returnDiskColon(String path) {
    if (path.charAt(1) == '_') {
      return path.charAt(0) + ":" + path.substring(2);
    }
    return path;
  }

  private String detect() {
    return new GitExecutableDetector() {
      @Override
      protected boolean runs(@NotNull String exec) {
        return CAN_RUN.contains(exec);
      }

      @Override
      protected String getPath() {
        return StringUtil.join(ContainerUtil.map(PATH.split(":"), new Function<String, String>() {
          @Override
          public String fun(String s) {
            return convertPath(s);
          }
        }), ";");
      }
    }.detect();
  }

  public static void setWindowsRoot(File file) throws NoSuchFieldException, IllegalAccessException {
    Field field = GitExecutableDetector.class.getDeclaredField("WIN_ROOT");
    field.setAccessible(true);

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    field.set(null, file);
  }

  private File testRoot;
  private ArrayList CAN_RUN = new ArrayList();
  private String PATH = "%SystemRoot%/system32;%SystemRoot%;%SystemRoot%/System32/Wbem;%SYSTEMROOT%/System32/WindowsPowerShell/v1.0/;";
}
