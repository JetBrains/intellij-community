// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;

@ApiStatus.Internal
public class DelegateKeyStore extends KeyStoreSpi {
  protected static Provider ourProvider = new Provider("IDEA", 1, "IDEA Key Store") {{
    Security.addProvider(this);
  }};

  private final KeyStore delegate;

  protected KeyStore getDelegate() {
    validate(delegate);
    return delegate;
  }

  protected void validate(KeyStore keyStore) {

  }

  @SuppressWarnings("SpellCheckingInspection")
  static String getDefaultKeyStorePath() {
    File base = new File(System.getProperty("java.home") + File.separator + "lib" + File.separator + "security");
    File jssecacerts = new File(base, "jssecacerts");
    return jssecacerts.exists() ? jssecacerts.getPath() : new File(base, "cacerts").getPath();
  }

  DelegateKeyStore(String type) {
    try {
      delegate = KeyStore.getInstance(type);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  DelegateKeyStore(KeyStore delegate) {
    this.delegate = delegate;
  }

  @Override
  public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
    try {
      return getDelegate().getKey(alias, password);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public java.security.cert.Certificate[] engineGetCertificateChain(String alias) {
    try {
      return getDelegate().getCertificateChain(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public java.security.cert.Certificate engineGetCertificate(String alias) {
    try {
      return getDelegate().getCertificate(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Date engineGetCreationDate(String alias) {
    try {
      return getDelegate().getCreationDate(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineSetKeyEntry(String alias, Key key, char[] password, java.security.cert.Certificate[] chain) throws KeyStoreException {
    getDelegate().setKeyEntry(alias, key, password, chain);
  }

  @Override
  public void engineSetKeyEntry(String alias, byte[] key, java.security.cert.Certificate[] chain) throws KeyStoreException {
    getDelegate().setKeyEntry(alias, key, chain);
  }

  @Override
  public void engineSetCertificateEntry(String alias, java.security.cert.Certificate cert) throws KeyStoreException {
    getDelegate().setCertificateEntry(alias, cert);
  }

  @Override
  public void engineDeleteEntry(String alias) throws KeyStoreException {
    getDelegate().deleteEntry(alias);
  }

  @Override
  public Enumeration<String> engineAliases() {
    try {
      return getDelegate().aliases();
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean engineContainsAlias(String alias) {
    try {
      return getDelegate().containsAlias(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public int engineSize() {
    try {
      return getDelegate().size();
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean engineIsKeyEntry(String alias) {
    try {
      return getDelegate().isKeyEntry(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean engineIsCertificateEntry(String alias) {
    try {
      return getDelegate().isCertificateEntry(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String engineGetCertificateAlias(Certificate cert) {
    try {
      return getDelegate().getCertificateAlias(cert);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    try {
      getDelegate().store(stream, password);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    delegate.load(stream, password);
  }


  @Nullable
  protected static KeyStore loadKeyStore(@NotNull String path, char[] password)
    throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
    File file = new File(path);
    if (!file.exists()) return null;
    KeyStore tmpStore = KeyStore.getInstance(KeyStore.getDefaultType());
    try (InputStream stream = new FileInputStream(file)) {
      tmpStore.load(stream, password);
    }
    return tmpStore;
  }
}
