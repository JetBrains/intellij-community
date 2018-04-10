package git4idea.crlf;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.Executor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.test.GitSingleRepoTest;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;

public class GitCrlfProblemsDetectorTest extends GitSingleRepoTest {
  public void setUp() throws Exception {
    try {
      super.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }
    Executor.cd(projectRoot);
    git("config core.autocrlf false");
  }

  public void test_no_warning_if_autocrlf_is_true() throws IOException {
    git("config core.autocrlf true");
    assertFalse("No warning should be done if core.autocrlf is true", detect("temp").shouldWarn());
  }

  public void test_no_warning_if_autocrlf_is_input() throws IOException {
    git("config core.autocrlf input");
    assertFalse("No warning should be done if core.autocrlf is input", detect("temp").shouldWarn());
  }

  public void test_no_warning_if_no_files_with_CRLF() throws IOException {
    createFile("temp", "Unix file\nNice separators\nOnly LF\n");
    assertFalse("No warning should be done if all files are LFs", detect("temp").shouldWarn());
  }

  public void test_no_warning_if_file_with_CRLF_but_text_is_set() throws IOException {
    gitattributes("*       text=auto");
    createCrlfFile("win");
    assertFalse("No warning should be done if the file has a text attribute", detect("win").shouldWarn());
  }

  public void test_no_warning_if_file_with_CRLF_but_crlf_is_set() throws IOException {
    gitattributes("win       crlf");
    createCrlfFile("win");
    assertFalse("No warning should be done if the file has a crlf attribute", detect("win").shouldWarn());
  }

  public void test_no_warning_if_file_with_CRLF_but_crlf_is_explicitly_unset() throws IOException {
    gitattributes("win       -crlf");
    createCrlfFile("win");
    assertFalse("No warning should be done if the file has an explicitly unset crlf attribute", detect("win").shouldWarn());
  }

  public void test_no_warning_if_file_with_CRLF_but_crlf_is_set_to_input() throws IOException {
    gitattributes("wi*       crlf=input");
    createCrlfFile("win");
    assertFalse("No warning should be done if the file has a crlf attribute", detect("win").shouldWarn());
  }

  public void test_warning_if_file_with_CRLF_no_attrs_autocrlf_is_false() throws IOException {
    createCrlfFile("win");
    assertTrue("Warning should be done if the file has CRLFs inside, and no explicit attributes", detect("win").shouldWarn());
  }

  public void test_warning_if_various_files_with_various_attributes_one_doesnt_match() throws IOException {
    gitattributes("\nwin1 text crlf diff\nwin2 -text\nwin3 text=auto\nwin4 crlf\nwin5 -crlf\nwin6 crlf=input\n");

    createFile("unix", "Unix file\nNice separators\nOnly LF\n");
    createCrlfFile("win1");
    createCrlfFile("win2");
    createCrlfFile("win3");
    createCrlfFile("src/win4");
    createCrlfFile("src/win5");
    createCrlfFile("src/win6");
    createCrlfFile("src/win7");

    List<VirtualFile> files = ContainerUtil.map(asList("unix", "win1", "win2", "win3", "src/win4", "src/win5", "src/win6", "src/win7"),
                                                s -> VfsUtil.findFileByIoFile(new File(projectRoot.getPath(), s), true));
    assertTrue("Warning should be done, since one of the files has CRLFs and no related attributes",
               GitCrlfProblemsDetector.detect(myProject, git, files).shouldWarn());
  }

  private void gitattributes(String content) throws IOException {
    createFile(".gitattributes", content);
  }

  private GitCrlfProblemsDetector detect(String relPath) throws IOException {
    return detect(VfsUtil.findFileByIoFile(createFile(relPath), true));
  }

  private GitCrlfProblemsDetector detect(VirtualFile file) {
    return GitCrlfProblemsDetector.detect(myProject, git, Collections.singleton(file));
  }

  private void createCrlfFile(String relPath) throws IOException {
    createFile(relPath, "Windows file\r\nBad separators\r\nCRLF in action\r\n");
  }

  private void createFile(String relPath, String content) throws IOException {
    File file = createFile(relPath);
    FileUtil.appendToFile(file, content);
  }

  private File createFile(String relPath) throws IOException {
    List<String> split = StringUtil.split(relPath, "/");
    File parent = new File(projectRoot.getPath());
    for (Iterator<String> it = split.iterator(); it.hasNext(); ) {
      String item = it.next();
      File file = new File(parent, item);
      if (it.hasNext()) {
        parent = file;
        parent.mkdir();
      }
      else {
        file.createNewFile();
        return file;
      }
    }

    return parent;
  }
}
