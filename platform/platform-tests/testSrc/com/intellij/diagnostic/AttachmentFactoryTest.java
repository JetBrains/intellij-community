package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class AttachmentFactoryTest {
  @Test
  public void testBigFilesStoredOnDisk() throws IOException {
    final File testFile = FileUtil.createTempFile("test", ".bin", true);
    try {
      FileUtil.writeToFile(testFile, new byte[150000]);
      Attachment attachment = AttachmentFactory.createAttachment(testFile, true);

      try (InputStream contentStream = attachment.openContentStream()) {
        Assert.assertTrue(contentStream instanceof FileInputStream);
      }
    } finally {
      //noinspection ResultOfMethodCallIgnored
      testFile.delete();
    }
  }

  @Test
  public void testSmallFilesStoredInMemory() throws IOException {
    final File testFile = FileUtil.createTempFile("test", ".bin", true);
    try {
      FileUtil.writeToFile(testFile, new byte[1500]);
      Attachment attachment = AttachmentFactory.createAttachment(testFile, true);

      try (InputStream contentStream = attachment.openContentStream()) {
        Assert.assertTrue(contentStream instanceof ByteArrayInputStream);
      }
    } finally {
      //noinspection ResultOfMethodCallIgnored
      testFile.delete();
    }
  }
}
