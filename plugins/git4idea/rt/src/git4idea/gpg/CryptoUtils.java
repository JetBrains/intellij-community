// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.gpg;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class CryptoUtils {

  public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }

  public static String publicKeyToString(PublicKey key) {
    byte[] keyBytes = key.getEncoded();
    if (keyBytes == null) return null;

    return Base64.getEncoder().encodeToString(keyBytes);
  }

  public static PublicKey stringToPublicKey(String keyStr) {
    byte[] keyBytes = Base64.getDecoder().decode(keyStr);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
    KeyFactory keyFactory;
    try {
      keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePublic(keySpec);
    }
    catch (GeneralSecurityException | IllegalArgumentException e) {
      return null;
    }
  }

  public static String encrypt(String payload, PrivateKey privateKey) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("RSA");
    cipher.init(Cipher.ENCRYPT_MODE, privateKey);
    return Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }

  public static String decrypt(String base64EncryptedMessage, PublicKey publicKey) throws GeneralSecurityException {
    byte[] encryptedMessage = Base64.getDecoder().decode(base64EncryptedMessage);
    Cipher cipher = Cipher.getInstance("RSA");
    cipher.init(Cipher.DECRYPT_MODE, publicKey);
    byte[] decryptedBytes = cipher.doFinal(encryptedMessage);
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }
}
