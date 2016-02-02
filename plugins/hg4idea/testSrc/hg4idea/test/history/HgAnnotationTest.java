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
package hg4idea.test.history;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.vcsUtil.VcsUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationProvider;

import java.io.File;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgAnnotationTest extends HgPlatformTest {
  HgAnnotationProvider myHgAnnotationProvider;

  static final String aName = "a.txt";
  static final String bName = "b.txt";
  static final String cName = "c.txt";
  static final String dName = "d.txt";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    cd(myRepository);
    File hgrc = new File(new File(myRepository.getPath(), ".hg"), "hgrc");
    FileUtil.appendToFile(hgrc, "[extensions]\n" +
                                "largefiles=!\n");
    touch(aName, "a1");
    myRepository.refresh(false, true);
    hg("add " + aName);
    hg("commit -m revision1");
    myHgAnnotationProvider = new HgAnnotationProvider(myProject);
  }
  // hg has a bug with status --copies command for this case: create a and b, simultaneously rename a->c,b->d; then c->b;d->a;
  //after that hg status --copies -r 1 "a" will return "a", but should return "b"; So we need to do renames in separated commits

  public void testAnnotationForCircleRename() throws VcsException {
    cd(myRepository);
    echo(aName, "2");
    hg("mv " + aName + " " + cName);
    myRepository.refresh(false, true);
    hg("commit -m revision2");
    touch(bName, "b1");
    hg("add " + bName);
    hg("commit -m revision3");
    echo(bName, "2");
    hg("mv " + bName + " " + dName);
    myRepository.refresh(false, true);
    hg("commit -m revision4");
    echo(cName, "3");
    hg("mv " + cName + " " + bName);
    myRepository.refresh(false, true);
    echo(dName, "3");
    hg("mv " + dName + " " + aName);
    myRepository.refresh(false, true);
    hg("commit -m revision5");
    myRepository.refresh(false, true);
    checkAnnotation(aName, "5", "b123");
    checkAnnotation(dName, "4", "b12");
    checkAnnotation(bName, "3", "b1");
    checkAnnotation(bName, "5", "a123");
    checkAnnotation(cName, "2", "a12");
    checkAnnotation(aName, "1", "a1");
  }

  private void checkAnnotation(String name, String revisionNum, String expectedContent) throws VcsException {
    FileAnnotation fileAnnotation = myHgAnnotationProvider
      .annotate(VcsUtil.getFilePath(new File(myRepository.getPath(), name)), HgRevisionNumber.getInstance(revisionNum, revisionNum));
    final HgRevisionNumber actualRevisionNumber = (HgRevisionNumber)fileAnnotation.getLineRevisionNumber(0);
    assert actualRevisionNumber != null;
    assertEquals(revisionNum, actualRevisionNumber.getRevision());
    assertEquals(expectedContent, fileAnnotation.getAnnotatedContent());
  }
}
