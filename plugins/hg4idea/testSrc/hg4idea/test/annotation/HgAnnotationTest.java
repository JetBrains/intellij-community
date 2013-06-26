package hg4idea.test.annotation;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.command.HgAnnotateCommand;
import org.zmlx.hg4idea.provider.annotate.HgAnnotation;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationLine;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.intellij.dvcs.test.Executor.cd;
import static com.intellij.dvcs.test.Executor.echo;
import static hg4idea.test.HgExecutor.hg;

/**
 * @author Nadya Zabrodina
 */
public class HgAnnotationTest extends HgPlatformTest {
  String firstCreatedFile = "file.txt";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myRepository);
    echo(firstCreatedFile, "a\n");
    hg("commit -m modify");
    echo(firstCreatedFile, "b\n");
    hg("commit -m modify1 -u 'a.bacaba@jetbrains.com' ");
    echo(firstCreatedFile, "c\n");
    hg("commit -m modify2 -u 'bacaba.a'");
  }

  public void testAnnotationWithVerboseOption() throws VcsException {
    final VirtualFile file = myRepository.findFileByRelativePath(firstCreatedFile);
    assert file != null;
    List<String> users = Arrays.asList("John Doe <John.Doe@example.com>", "a.bacaba@jetbrains.com", "bacaba.a");
    final HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(file));
    final String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    List<HgAnnotationLine> annotationLines =
      new HgAnnotateCommand(myProject).execute(hgFile, null);
    for (int i = 0; i < annotationLines.size(); ++i) {
      HgAnnotationLine line = annotationLines.get(i);
      assertEquals(users.get(i), line.get(HgAnnotation.FIELD.USER));
      assertEquals(date, line.get(HgAnnotation.FIELD.DATE));
    }
  }
}
