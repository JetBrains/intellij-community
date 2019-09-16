// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;

public class DelegateKeyStore extends KeyStoreSpi {
  protected static Provider ourProvider = new Provider("IDEA", 1, "IDEA Key Store") {{
    Security.addProvider(this);
  }};

  protected final KeyStore delegate;

  public DelegateKeyStore(String type) {
    try {
      delegate = KeyStore.getInstance(type);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  public DelegateKeyStore(KeyStore delegate) {
    this.delegate = delegate;
  }

  @Override
  public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
    try {
      return delegate.getKey(alias, password);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public java.security.cert.Certificate[] engineGetCertificateChain(String alias) {
    try {
      return delegate.getCertificateChain(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public java.security.cert.Certificate engineGetCertificate(String alias) {
    try {
      return delegate.getCertificate(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Date engineGetCreationDate(String alias) {
    try {
      return delegate.getCreationDate(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineSetKeyEntry(String alias, Key key, char[] password, java.security.cert.Certificate[] chain) throws KeyStoreException {
    delegate.setKeyEntry(alias, key, password, chain);
  }

  @Override
  public void engineSetKeyEntry(String alias, byte[] key, java.security.cert.Certificate[] chain) throws KeyStoreException {
    delegate.setKeyEntry(alias, key, chain);
  }

  @Override
  public void engineSetCertificateEntry(String alias, java.security.cert.Certificate cert) throws KeyStoreException {
    delegate.setCertificateEntry(alias, cert);
  }

  @Override
  public void engineDeleteEntry(String alias) throws KeyStoreException {
    delegate.deleteEntry(alias);
  }

  @Override
  public Enumeration<String> engineAliases() {
    try {
      return delegate.aliases();
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean engineContainsAlias(String alias) {
    try {
      return delegate.containsAlias(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public int engineSize() {
    try {
      return delegate.size();
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean engineIsKeyEntry(String alias) {
    try {
      return delegate.isKeyEntry(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean engineIsCertificateEntry(String alias) {
    try {
      return delegate.isCertificateEntry(alias);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String engineGetCertificateAlias(Certificate cert) {
    try {
      return delegate.getCertificateAlias(cert);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    try {
      delegate.store(stream, password);
    }
    catch (KeyStoreException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
    delegate.load(stream, password);
  }
}
