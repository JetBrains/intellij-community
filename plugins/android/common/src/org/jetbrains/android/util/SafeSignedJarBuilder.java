package org.jetbrains.android.util;

import com.android.jarutils.SignedJarBuilder;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author Eugene.Kudelevsky
 */
public class SafeSignedJarBuilder extends SignedJarBuilder {
  private final String myOutFilePath;

  public SafeSignedJarBuilder(FileOutputStream outputStream, PrivateKey key, X509Certificate certificate, String outFilePath)
    throws IOException, NoSuchAlgorithmException {
    super(outputStream, key, certificate);
    myOutFilePath = FileUtil.toSystemDependentName(outFilePath);
  }

  @Override
  public void writeFile(File inputFile, String jarPath) throws IOException {
    if (FileUtil.pathsEqual(inputFile.getPath(), myOutFilePath)) {
      throw new IOException("Cannot pack file " + myOutFilePath + " into itself");
    }
    super.writeFile(inputFile, jarPath);
  }
}
