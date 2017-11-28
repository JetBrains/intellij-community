package hg4idea.test.annotation;

import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.provider.annotate.HgAnnotation;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgAnnotationTest extends HgPlatformTest {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
  private static final String firstCreatedFile = "file.txt";
  private static final String author1 = "a.bacaba@jetbrains.com";
  private static final String author2 = "bacaba.a";
  private static final String defaultAuthor = "John Doe <John.Doe@example.com>";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Calendar calendar = Calendar.getInstance();
    calendar.set(2005, Calendar.DECEMBER, 24, 15, 10);
    Date time = calendar.getTime();
    Clock.setTime(time);
    cd(myRepository);
    String dateArg = " -d '" + DATE_FORMAT.format(time) + "'";
    echo(firstCreatedFile, "a\n");
    hg("commit -m modify -u '" + defaultAuthor + "'" + dateArg);
    echo(firstCreatedFile, "b\n");
    hg("commit -m modify1 -u " + author1 + dateArg);
    echo(firstCreatedFile, "c\n");
    hg("commit -m modify2 -u " + author2 + dateArg);
  }

  @Override
  protected void tearDown() throws Exception {
    Clock.reset();
    super.tearDown();
  }

  public void testAnnotationWithVerboseOption() {
    myRepository.refresh(false, true);
    final VirtualFile file = myRepository.findFileByRelativePath(firstCreatedFile);
    assert file != null;
    List<String> users = Arrays.asList(defaultAuthor, author1, author2);
    final HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(file));
    final String date = DateFormatUtil.formatPrettyDate(Clock.getTime());
    List<HgAnnotationLine> annotationLines =
      new HgAnnotateCommand(myProject).execute(hgFile, null);
    for (int i = 0; i < annotationLines.size(); ++i) {
      HgAnnotationLine line = annotationLines.get(i);
      assertEquals(users.get(i), line.get(HgAnnotation.FIELD.USER));
      assertEquals(date, line.get(HgAnnotation.FIELD.DATE));
    }
  }

  public void testAnnotationWithIgnoredWhitespaces() {
    annotationWithWhitespaceOption(true);
  }

  public void testAnnotationWithoutIgnoredWhitespaces() {
    annotationWithWhitespaceOption(false);
  }

  private void annotationWithWhitespaceOption(boolean ignoreWhitespaces) {
    cd(myRepository);
    String whitespaceFile = "whitespaces.txt";
    touch(whitespaceFile, "not whitespaces");
    myRepository.refresh(false, true);
    String whiteSpaceAuthor = "Mr.Whitespace";
    final VirtualFile file = myRepository.findFileByRelativePath(whitespaceFile);
    assert file != null;
    hg("add " + whitespaceFile);
    hg("commit -m modify -u '" + defaultAuthor + "'");
    echo(whitespaceFile, "    ");//add several whitespaces
    hg("commit -m whitespaces -u '" + whiteSpaceAuthor + "'");
    final HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(file));
    myVcs.getProjectSettings().setIgnoreWhitespacesInAnnotations(ignoreWhitespaces);
    List<HgAnnotationLine> annotationLines =
      new HgAnnotateCommand(myProject).execute(hgFile, null);
    HgAnnotationLine line = annotationLines.get(0);
    assertEquals(ignoreWhitespaces ? defaultAuthor : whiteSpaceAuthor, line.get(HgAnnotation.FIELD.USER));
  }
}
