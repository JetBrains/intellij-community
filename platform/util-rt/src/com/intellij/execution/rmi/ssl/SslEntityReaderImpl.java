// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Base64;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class SslEntityReaderImpl extends SslEntityReader {
  private static final String P1_BEGIN_MARKER = "-----BEGIN RSA PRIVATE KEY";
  private static final String P8_BEGIN_MARKER = "-----BEGIN PRIVATE KEY";
  private static final String EP8_BEGIN_MARKER = "-----BEGIN ENCRYPTED PRIVATE KEY";
  private static final String OTHER_BEGIN_MARKER = "-----BEGIN";
  private static final String OTHER_END_MARKER = "-----END";

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

  @NotNull
  private static List<? extends Entity> readDer(@NotNull InputStream stream) throws IOException {
    return Collections.singletonList(readDerKey(FileUtilRt.loadBytes(stream)));
  }

  @NotNull
  private static PrivateKeyEntity readDerKey(final byte[] bytes) {
    try {
      UnencryptedPrivateKeyEntity unencrypted = new PKCS8PrivateKey(bytes);
      PrivateKey ignored = unencrypted.get();
      return unencrypted;
    }
    catch (Throwable ignored) {
      return new EncryptedPrivateKeyImpl(bytes);
    }
  }

  @NotNull
  private static List<? extends Entity> readPem(@NotNull InputStream stream) throws IOException {
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

  @Nullable
  private static Entity readPemEntity(String line, BufferedReader reader) throws IOException {
    if (!line.startsWith(OTHER_BEGIN_MARKER)) {
      return null;
    }
    if (line.startsWith(P1_BEGIN_MARKER)) {
      return new RSAPrivateKey(readPemEntryBytes(reader));
    }
    if (line.startsWith(P8_BEGIN_MARKER)) {
      return new PKCS8PrivateKey(readPemEntryBytes(reader));
    }
    if (line.startsWith(EP8_BEGIN_MARKER)) {
      return new EncryptedPrivateKeyImpl(readPemEntryBytes(reader));
    }
    return new PemCertificate(readPemEntryText(reader, line).toString());
  }

  private static byte[] readPemEntryBytes(BufferedReader reader) throws IOException {
    return Base64.decode(readPemEntryText(reader, null).toString());
  }

  @NotNull
  private static CharSequence readPemEntryText(BufferedReader reader, String firstLine) throws IOException {
    StringBuilder builder = new StringBuilder();
    if (firstLine != null) builder.append(firstLine).append('\n');
    while (true) {
      String tmp = reader.readLine();
      if (tmp.startsWith(OTHER_END_MARKER)) {
        if (firstLine != null) builder.append(tmp).append('\n');
        break;
      }
      builder.append(tmp);
      if (firstLine != null) builder.append('\n');
    }
    return builder;
  }

  public static class PKCS8PrivateKey extends UnencryptedPrivateKeyImpl {
    private PKCS8PrivateKey(byte @NotNull [] bytes) {
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
    private RSAPrivateKey(byte @NotNull [] bytes) {
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

  private abstract static class UnencryptedPrivateKeyImpl implements UnencryptedPrivateKeyEntity {
    protected final byte[] myBytes;
    private PrivateKey myKey;

    private UnencryptedPrivateKeyImpl(byte @NotNull [] bytes) {
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

    private EncryptedPrivateKeyImpl(byte @NotNull [] bytes) {
      myBytes = bytes;
    }

    @Override
    public PrivateKey get(char[] password) throws IOException {
      try {
        PKCS8EncodedKeySpec spec = createEncryptedKeySpec(myBytes, password);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(spec);
      }
      catch (Exception e) {
        throw new IOException("Failed to parse encrypted key", e);
      }
    }

    private static PKCS8EncodedKeySpec createEncryptedKeySpec(byte[] keyBytes, char[] password) throws IOException {
      EncryptedPrivateKeyInfo encrypted = new EncryptedPrivateKeyInfo(keyBytes);
      PBEKeySpec encryptedKeySpec = new PBEKeySpec(password);
      try {
        SecretKeyFactory pbeKeyFactory = SecretKeyFactory.getInstance(encrypted.getAlgName());
        return encrypted.getKeySpec(pbeKeyFactory.generateSecret(encryptedKeySpec));
      }
      catch (GeneralSecurityException e) {
        throw new IOException("JCE error: " + e.getMessage(), e);
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