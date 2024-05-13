// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SslEntityReaderImpl extends SslEntityReader {
  private static final String BEGIN_MARK = "-----BEGIN";

  @Override
  @NotNull
  public Pair<PrivateKey, List<X509Certificate>> readPrivateKeyAndCertificate(@NotNull String filePath, @Nullable char[] password)
    throws IOException {
    try (FileInputStream stream = new FileInputStream(filePath)) {
      List<? extends Entity> entities = read(stream);
      PrivateKey key = null;
      List<X509Certificate> certs = new ArrayList<>();
      for (Entity entity : entities) {
        if (entity instanceof EncryptedPrivateKeyEntity) {
          key = ((EncryptedPrivateKeyEntity)entity).get(password);
        }
        else if (entity instanceof UnencryptedPrivateKeyEntity) {
          key = ((UnencryptedPrivateKeyEntity)entity).get();
        }
        else if (entity instanceof CertificateEntity) {
          certs.add(((CertificateEntity)entity).get());
        }
      }
      if (key == null) {
        throw new IOException("Failed to find key in file " + filePath);
      }
      return Pair.create(key, certs.isEmpty() ? null : certs);
    }
  }

  @Override
  @NotNull
  public List<X509Certificate> loadCertificates(@NotNull String caCertPath) throws CertificateException, IOException {
    try (FileInputStream stream = new FileInputStream(caCertPath)) {
      return loadCertificates(stream);
    }
  }

  private @NotNull List<X509Certificate> loadCertificates(@NotNull InputStream stream) throws IOException {
    List<? extends Entity> entities = read(stream);
    List<X509Certificate> certs = new ArrayList<>();
    for (Entity entity : entities) {
      if (entity instanceof CertificateEntity) {
        certs.add(((CertificateEntity)entity).get());
      }
    }
    return certs;
  }

  @Override
  @NotNull
  public X509Certificate readCertificate(@NotNull InputStream stream) throws CertificateException, IOException {
    try (InputStream ignored = stream) {
      List<X509Certificate> certificates = loadCertificates(stream);
      if (certificates.isEmpty()) {
        throw new IOException("Certificate not found");
      }
      return certificates.get(0);
    }
  }

  @Override
  @NotNull
  public PrivateKey readPrivateKey(@NotNull String filePath, @Nullable char[] password) throws IOException {
    return readPrivateKeyAndCertificate(filePath, password).first;
  }

  @NotNull
  @Override
  public List<? extends Entity> read(@NotNull InputStream stream) throws IOException {
    try (PushbackInputStream pStream = new PushbackInputStream(stream)) {
      int peeked = pStream.read();
      pStream.unread(peeked);
      if (peeked == 48) {
        return readDer(pStream);
      }
      else {
        return readPem(pStream);
      }
    }
  }

  private static @NotNull List<? extends Entity> readDer(@NotNull InputStream stream) throws IOException {
    return Collections.singletonList(readDerKey(FileUtilRt.loadBytes(stream)));
  }

  private static @NotNull PrivateKeyEntity readDerKey(final byte[] bytes) {
    try {
      UnencryptedPrivateKeyEntity unencrypted = new PKCS8PrivateKey(bytes);
      PrivateKey ignored = unencrypted.get();
      return unencrypted;
    }
    catch (Throwable ignored) {
      return new EncryptedPrivateKeyImpl(bytes);
    }
  }

  private static @NotNull List<? extends Entity> readPem(@NotNull InputStream stream) throws IOException {
    List<Entity> result = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      while (true) {
        String line = reader.readLine();
        if (line == null) break;
        Entity key = readPemEntity(line, reader);
        if (key != null) {
          result.add(key);
        }
      }
    }
    return result;
  }

  private static @Nullable Entity readPemEntity(String line, BufferedReader reader) throws IOException {
    if (!line.startsWith(PrivateKeyReader.OTHER_BEGIN_MARKER)) {
      return null;
    }
    if (line.startsWith(PrivateKeyReader.P1_BEGIN_MARKER)) {
      return new RSAPrivateKey(readPemEntryBytes(reader));
    }
    if (line.startsWith(PrivateKeyReader.P8_BEGIN_MARKER)) {
      return new PKCS8PrivateKey(readPemEntryBytes(reader));
    }
    if (line.startsWith(PrivateKeyReader.EP8_BEGIN_MARKER)) {
      return new EncryptedPrivateKeyImpl(readPemEntryBytes(reader));
    }
    return new PemCertificate(readPemEntryText(reader, line).toString());
  }

  private static byte[] readPemEntryBytes(BufferedReader reader) throws IOException {
    return Base64.decode(readPemEntryText(reader, null).toString());
  }

  private static @NotNull CharSequence readPemEntryText(BufferedReader reader, String firstLine) throws IOException {
    StringBuilder builder = new StringBuilder();
    if (firstLine != null) builder.append(firstLine).append('\n');
    while (true) {
      String tmp = reader.readLine();
      if (tmp.startsWith(PrivateKeyReader.OTHER_END_MARKER)) {
        if (firstLine != null) builder.append(tmp).append('\n');
        break;
      }
      builder.append(tmp);
      if (firstLine != null) builder.append('\n');
    }
    return builder;
  }

  public static class PKCS8PrivateKey extends UnencryptedPrivateKeyImpl {
    private PKCS8PrivateKey(@NotNull byte[] bytes) {
      super(bytes);
    }

    @Override
    protected KeySpec getKeySpec() throws IOException {
      return new PKCS8EncodedKeySpec(myBytes);
    }

    @Override
    protected String getEnc() throws IOException {
      return "PKCS#8";
    }
  }

  public static class RSAPrivateKey extends UnencryptedPrivateKeyImpl {
    private RSAPrivateKey(@NotNull byte[] bytes) {
      super(bytes);
    }

    @Override
    protected KeySpec getKeySpec() throws IOException {
      return PrivateKeyReader.getRSAKeySpec(myBytes);
    }

    @Override
    protected String getEnc() throws IOException {
      return "PKCS#1";
    }
  }

  private static abstract class UnencryptedPrivateKeyImpl implements UnencryptedPrivateKeyEntity {
    protected final byte[] myBytes;
    private PrivateKey myKey;

    private UnencryptedPrivateKeyImpl(@NotNull byte[] bytes) {
      myBytes = bytes;
    }

    protected abstract KeySpec getKeySpec() throws IOException;
    protected abstract String getEnc() throws IOException;

    @Override
    public PrivateKey get() throws IOException {
      if (myKey == null) {
        try {
          KeyFactory factory = KeyFactory.getInstance("RSA");
          myKey = factory.generatePrivate(getKeySpec());
        }
        catch (Exception e) {
          throw new IOException("Failed to parse " + getEnc() + " key", e);
        }
      }
      return myKey;
    }
  }
  private static class EncryptedPrivateKeyImpl implements EncryptedPrivateKeyEntity {
    protected final byte[] myBytes;

    private EncryptedPrivateKeyImpl(@NotNull byte[] bytes) {
      myBytes = bytes;
    }

    @Override
    public PrivateKey get(char[] password) throws IOException {
      try {
        PKCS8EncodedKeySpec spec = PrivateKeyReader.createEncryptedKeySpec(myBytes, password);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(spec);
      }
      catch (Exception e) {
        throw new IOException("Failed to parse encrypted key", e);
      }
    }
  }
  private static class PemCertificate implements CertificateEntity {
    private final String myText;
    private X509Certificate myCertificate;

    private PemCertificate(@NotNull String text) {
      myText = text;
    }

    @Override
    public X509Certificate get() throws IOException {
      if (myCertificate == null) {
        try {
          CertificateFactory factory = CertificateFactory.getInstance("X.509");
          ByteArrayInputStream stream = new ByteArrayInputStream(myText.getBytes(StandardCharsets.UTF_8));
          myCertificate = (X509Certificate)factory.generateCertificate(stream);
        }
        catch (Exception e) {
          throw new IOException("Failed to read certificate", e);
        }
      }
      return myCertificate;
    }
  }
}