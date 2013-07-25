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
package hg4idea.test.version;

import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgVersionCommand;

/**
 * @author Nadya Zabrodina
 */
public class HgVersionTest extends HgPlatformTest {

  @NotNull private HgVcs myVcs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    HgVcs vcs = HgVcs.getInstance(myProject);
    assertNotNull(vcs);
    myVcs = vcs;
  }

  public void testVersionCommandForCurrentHgVersion() {
    Double version =
      new HgVersionCommand().getVersion(myVcs.getGlobalSettings().getHgExecutable(), myVcs.getGlobalSettings().isRunViaBash());
    assertNotNull(version);
    assertTrue(version > 0);
  }
}
