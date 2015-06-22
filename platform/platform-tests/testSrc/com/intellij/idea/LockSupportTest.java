/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.idea;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class LockSupportTest {
  @Test(timeout = 30000)
  public void testLock() throws Exception {
    File temp = FileUtil.createTempDirectory("c", null);
    SocketLock lock = new SocketLock(temp.getPath() + "/c", temp.getPath() + "/s");
    try {
      assertThat(lock.lock(), equalTo(SocketLock.ActivateStatus.NO_INSTANCE));
    }
    finally {
      lock.dispose();

      FileUtil.delete(temp);
    }
  }

  @Test(timeout = 30000)
  public void testTwoLocks() throws Exception {
    List<SocketLock> toClose = new ArrayList<SocketLock>();
    File temp = FileUtil.createTempDirectory("c", null);
    try {
      assertThat(createLock(toClose, temp, "1", "1-").lock(), equalTo(SocketLock.ActivateStatus.NO_INSTANCE));
      assertThat(createLock(toClose, temp, "1.1", "1-1").lock(), equalTo(SocketLock.ActivateStatus.NO_INSTANCE));
      assertThat(createLock(toClose, temp, "2", "2-").lock(), equalTo(SocketLock.ActivateStatus.NO_INSTANCE));

      assertThat(createLock(toClose, temp, "2", "2-").lock(), equalTo(SocketLock.ActivateStatus.ACTIVATED));
      assertThat(createLock(toClose, temp, "1", "1-").lock(), equalTo(SocketLock.ActivateStatus.ACTIVATED));
      assertThat(createLock(toClose, temp, "1.1", "1-1").lock(), equalTo(SocketLock.ActivateStatus.ACTIVATED));
    }
    finally {
      for (SocketLock lock : toClose) {
        lock.dispose();
      }

      FileUtil.delete(temp);
    }
  }

  @NotNull
  private static SocketLock createLock(@NotNull List<SocketLock> toClose, @NotNull File dir, @NotNull String c, @NotNull String s) {
    SocketLock lock = new SocketLock(dir.getPath() + "/" + c, dir.getPath() + "/" + s);
    toClose.add(lock);
    return lock;
  }

  @Test(timeout = 30000)
  public void testDispose() throws Exception {
    File temp = FileUtil.createTempDirectory("c", null);

    SocketLock lock1 = new SocketLock(temp.getPath() + "/1", temp.getPath() + "/1-");
    SocketLock lock2 = new SocketLock(temp.getPath() + "/1", temp.getPath() + "/1-");

    try {
      assertThat(lock1.lock(), equalTo(SocketLock.ActivateStatus.NO_INSTANCE));
      assertThat(lock2.lock(), equalTo(SocketLock.ActivateStatus.ACTIVATED));

      lock1.dispose();
      assertThat(lock2.lock(), equalTo(SocketLock.ActivateStatus.NO_INSTANCE));
      lock2.dispose();
    }
    finally {
      FileUtil.delete(temp);
    }
  }
}
