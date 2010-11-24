/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.tests;

import git4idea.history.wholeTree.AbstractHash;
import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * @author irengrig
 */
public class AbstractHashTest extends TestCase {
  public void testSimple() throws Exception {
    final String hash = "0a5b9f";
    final AbstractHash abstractHash = AbstractHash.create(hash);
    Assert.assertFalse(abstractHash.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash, abstractHash.getString());
  }
  public void testShort() throws Exception {
    final String hash = "f";
    final AbstractHash abstractHash = AbstractHash.create(hash);
    Assert.assertFalse(abstractHash.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash, abstractHash.getString());

    final String hash1 = "ff";
    final AbstractHash abstractHash1 = AbstractHash.create(hash1);
    Assert.assertFalse(abstractHash1.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash1, abstractHash1.getString());
  }
  public void testLong() throws Exception {
    final String hash = "0123456789abcdef0123456789abcdef01234567";
    final AbstractHash abstractHash = AbstractHash.create(hash);
    Assert.assertFalse(abstractHash.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash, abstractHash.getString());
  }
  public void testLeadingNulls() throws Exception {
    final String hash0 = "0";
    final AbstractHash abstractHash0 = AbstractHash.create(hash0);
    Assert.assertFalse(abstractHash0.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash0, abstractHash0.getString());

    final String hash = "0f";
    final AbstractHash abstractHash = AbstractHash.create(hash);
    Assert.assertFalse(abstractHash.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash, abstractHash.getString());

    final String hash1 = "001";
    final AbstractHash abstractHash1 = AbstractHash.create(hash1);
    Assert.assertFalse(abstractHash1.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash1, abstractHash1.getString());

    final String hash2 = "000";
    final AbstractHash abstractHash2 = AbstractHash.create(hash2);
    Assert.assertFalse(abstractHash2.getClass().getName().contains("AbstractHash.StringPresentation"));
    Assert.assertEquals(hash2, abstractHash2.getString());
  }
}
