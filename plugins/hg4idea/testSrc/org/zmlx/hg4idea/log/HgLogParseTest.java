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
package org.zmlx.hg4idea.log;

import com.intellij.openapi.util.Couple;
import org.junit.Test;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class HgLogParseTest {

  @Test
  public void testParseFileCopiesWithWhitespaces() {
    Map<String, String> filesMap = HgFileRevisionLogParser.parseCopiesFileList(
      "/a/b c/d.txt (a/b a/d.txt)\u0001/a/b c/(d).txt (/a/b c/(f).txt)");
    assertTrue(filesMap.containsKey("a/b a/d.txt"));
    assertTrue(filesMap.containsKey("/a/b c/(f).txt"));
    assertTrue(filesMap.containsValue("/a/b c/d.txt"));
    assertTrue(filesMap.containsValue("/a/b c/(d).txt"));
  }

  @Test
  public void testParseFileCopiesOldVersion() {
    Map<String, String> filesMap = HgFileRevisionLogParser.parseCopiesFileListAsOldVersion(
      "/a/b c/d.txt (a/b a/d.txt)/a/b c/(d).txt (/a/b c/(f).txt)");
    assertTrue(filesMap.containsKey("a/b a/d.txt"));
    assertTrue(filesMap.containsKey("/a/b c/(f).txt"));
    assertTrue(filesMap.containsValue("/a/b c/d.txt"));
    assertTrue(filesMap.containsValue("/a/b c/(d).txt"));
  }

  @Test
  public void testParseUserNameAndEmail() {
    assertEquals(HgUtil.parseUserNameAndEmail("Vasya Pavlovich Pupkin <asdasd@localhost>"),
                 new Couple<>("Vasya Pavlovich Pupkin", "asdasd@localhost"));
    assertEquals(HgUtil.parseUserNameAndEmail("Vasya Pavlovich Pupkin"), new Couple<>("Vasya Pavlovich Pupkin", ""));
    assertEquals(HgUtil.parseUserNameAndEmail("vasya.pupkin@localhost.com"),
                 new Couple<>("", "vasya.pupkin@localhost.com"));
    assertEquals(HgUtil.parseUserNameAndEmail("<vasya.pupkin@localhost.com>"),
                 new Couple<>("", "vasya.pupkin@localhost.com"));
    assertEquals(HgUtil.parseUserNameAndEmail("<vasya@localhost.com>"),
                 new Couple<>("", "vasya@localhost.com"));
    assertEquals(HgUtil.parseUserNameAndEmail("Vasya <vas@localhost.com>"),
                 new Couple<>("Vasya", "vas@localhost.com"));
  }
}
