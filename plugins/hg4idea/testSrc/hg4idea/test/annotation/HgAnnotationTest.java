package hg4idea.test.annotation;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.provider.annotate.HgAnnotation;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.echo;
import static hg4idea.test.HgExecutor.hg;

/**
 * @author Nadya Zabrodina
 */
public class HgAnnotationTest extends HgPlatformTest {
  String firstCreatedFile = "file.txt";
  static final String author1 = "a.bacaba@jetbrains.com";
  static final String author2 = "bacaba.a";
  static final String defaultAuthor = "John Doe <John.Doe@example.com>";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myRepository);
    echo(firstCreatedFile, "a\n");
    hg("commit -m modify -u '" + defaultAuthor + "'");
    echo(firstCreatedFile, "b\n");
    hg("commit -m modify1 -u " + author1);
    echo(firstCreatedFile, "c\n");
    hg("commit -m modify2 -u " + author2);
  }

  public void testAnnotationWithVerboseOption() throws VcsException {
    final VirtualFile file = myRepository.findFileByRelativePath(firstCreatedFile);
    assert file != null;
    List<String> users = Arrays.asList(defaultAuthor, author1, author2);
    final HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(file));
    final String date = DateFormatUtil.formatPrettyDate(new Date());
    List<HgAnnotationLine> annotationLines =
      new HgAnnotateCommand(myProject).execute(hgFile, null);
    for (int i = 0; i < annotationLines.size(); ++i) {
      HgAnnotationLine line = annotationLines.get(i);
      assertEquals(users.get(i), line.get(HgAnnotation.FIELD.USER));
      assertEquals(date, line.get(HgAnnotation.FIELD.DATE));
    }
  }
}
