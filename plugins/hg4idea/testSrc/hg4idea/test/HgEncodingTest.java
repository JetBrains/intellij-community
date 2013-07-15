/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test;

import com.intellij.openapi.vcs.VcsException;
import org.zmlx.hg4idea.command.HgCommitCommand;
import org.zmlx.hg4idea.execution.HgCommandException;

import static com.intellij.dvcs.test.Executor.cd;
import static com.intellij.dvcs.test.Executor.echo;

/**
 * @author Nadya Zabrodina
 */
public class HgEncodingTest extends HgPlatformTest {

  //test for  default EncodingProject settings
  public void testCommitUtfMessage() throws HgCommandException, VcsException {
    cd(myRepository);
    echo("file.txt", "lalala");
    HgCommitCommand commitCommand = new HgCommitCommand(myProject, myRepository, "сообщение");
    commitCommand.execute();
  }
}
