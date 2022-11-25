// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.TemporaryDirectory;
import junit.framework.TestCase;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class FilePropertyKeyImplTest<T> extends LightPlatformTestCase {
  @Rule
  public TemporaryDirectory tempDir = new TemporaryDirectory();

  private enum TestEnum {
    ONE, TWO
  }

  private static final FilePropertyKey<String> STRING_KEY =
    FilePropertyKeyImpl.createPersistentStringKey("test_string_attr", new FileAttribute("string_key", 1, true));
  private static final FilePropertyKey<TestEnum> ENUM_KEY =
    FilePropertyKeyImpl.createPersistentEnumKey("test_enum_attr", "test_enum_attr", 1, TestEnum.class);

  @Parameter(0)
  public FilePropertyKey<T> key;
  @Parameter(1)
  public T sample1;
  @Parameter(2)
  public T sample2;

  private Key<?> memKey;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    memKey = ((FilePropertyKeyImpl<T, ?>)key).getUserDataKey();
  }

  private VirtualFile createVirtualFile(String name, String content) {
    VirtualFile file = tempDir.createVirtualFile(name, content);

    // this should be new physical file with no any data associated with it
    assertTrue(file.getClass().getName(), file instanceof VirtualFileWithId);
    assertNull(file.toString(), memKey.get(file));
    assertNull(file.toString(), key.getPersistentValue(file));
    return file;
  }

  @Test
  public void testLightVirtualFile() {
    LightVirtualFile file = new LightVirtualFile();
    TestCase.assertFalse("Precondition failed", file instanceof VirtualFileWithId);

    TestCase.assertTrue(key.setPersistentValue(file, sample1));
    TestCase.assertEquals(sample1, key.getPersistentValue(file));
    TestCase.assertTrue(key.setPersistentValue(file, null));
    TestCase.assertNull(key.getPersistentValue(file));
  }

  @Test
  public void testReadFromFile_PersistenceDisabled() {
    Assume.assumeFalse(FilePropertyKeyImpl.getREAD_PERSISTENT_VALUE());
    VirtualFile file = createVirtualFile("Foo.java", "");
    TestCase.assertTrue("Write sample1 to file", key.setPersistentValue(file, sample1));
    TestCase.assertEquals(sample1, key.getPersistentValue(file));

    memKey.set(file, null); // clear memory data

    TestCase.assertNull("Should read null from memory (before write)", key.getPersistentValue(file));
    TestCase.assertFalse("Should not update sample1>sample1 value in file", key.setPersistentValue(file, sample1));
    TestCase.assertEquals("Should read sample1 from memory (after write)", sample1, key.getPersistentValue(file));
  }

  @Test
  public void testReadFromFile_PersistenceEnabled() {
    Assume.assumeTrue(FilePropertyKeyImpl.getREAD_PERSISTENT_VALUE());
    VirtualFile file = createVirtualFile("Foo.java", "");
    TestCase.assertTrue("Write sample1 to file", key.setPersistentValue(file, sample1));
    TestCase.assertEquals(sample1, key.getPersistentValue(file));

    memKey.set(file, null); // clear memory data
    TestCase.assertEquals("Should read previous value from file", sample1, key.getPersistentValue(file));
    TestCase.assertFalse("Should not update sample1>sample1 value in file", key.setPersistentValue(file, sample1));
    TestCase.assertEquals("Should read sample1 from memory (after write)", sample1, key.getPersistentValue(file));

    TestCase.assertTrue("Write null to file", key.setPersistentValue(file, null));
    TestCase.assertNull(key.getPersistentValue(file));
    memKey.set(file, null); // clear memory data
    TestCase.assertNull("Should read null from file", key.getPersistentValue(file));
  }

  @Test
  public void testGetFromEmpty() {
    VirtualFile file = createVirtualFile("Foo.java", "");
    TestCase.assertNull("No previous value in vfs, should read null", key.getPersistentValue(file));
    TestCase.assertNull("Second read should also return null", key.getPersistentValue(file));
  }

  @Test
  public void testSetGetTwoSamples() {
    VirtualFile file = createVirtualFile("Foo.java", "");
    TestCase.assertTrue("First set should change null to sample1", key.setPersistentValue(file, sample1));
    TestCase.assertFalse("Second set should not change existing value", key.setPersistentValue(file, sample1));
    TestCase.assertEquals(sample1, key.getPersistentValue(file));

    TestCase.assertTrue("First set should change sample1 to sample2", key.setPersistentValue(file, sample2));
    TestCase.assertFalse("Second set should not change existing value", key.setPersistentValue(file, sample2));
    TestCase.assertEquals(sample2, key.getPersistentValue(file));
  }

  @Test
  public void testSetGetNulls() {
    VirtualFile file = createVirtualFile("Foo.java", "");
    TestCase.assertNull("Initial value is null", key.getPersistentValue(file));
    TestCase.assertTrue("Write something not-null", key.setPersistentValue(file, sample1));
    TestCase.assertEquals(sample1, key.getPersistentValue(file));

    TestCase.assertTrue("Write null, should change previous value", key.setPersistentValue(file, null));
    TestCase.assertFalse("Second set should not change existing value", key.setPersistentValue(file, null));
    TestCase.assertNull("We still can read null", key.getPersistentValue(file));
  }

  @Test
  public void testStringNotModifiedWhenPersisted(){
    Assume.assumeTrue(key == STRING_KEY);
    VirtualFile file = createVirtualFile("Foo.java", "");

    List<String> values = Arrays.asList("value", "s p a c e s", "," ,"", " ", "\t", "null", null);
    for (String value : values) {
      STRING_KEY.setPersistentValue(file, value);
      assertEquals("Should read exactly the same string (read from memory)", value, STRING_KEY.getPersistentValue(file));

      memKey.set(file, null); // clear memory data
      assertFalse("Should not update to the same string: '" + value + "'", STRING_KEY.setPersistentValue(file, value));
      assertEquals("Should read exactly the same string (read from memory)", value, STRING_KEY.getPersistentValue(file));

      if (!FilePropertyKeyImpl.getREAD_PERSISTENT_VALUE()) continue;
      memKey.set(file, null); // clear memory data
      assertEquals("Should read exactly the same string (read from file)", value, STRING_KEY.getPersistentValue(file));
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection samples() {
    return Arrays.asList(new Object[][]{
      {STRING_KEY, "sample1", "sample2"},
      {ENUM_KEY, TestEnum.ONE, TestEnum.TWO}
    });
  }
}