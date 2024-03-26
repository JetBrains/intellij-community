// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi.ssl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.*;

final class PrivateKeyReader {
  public static final String P1_BEGIN_MARKER = "-----BEGIN RSA PRIVATE KEY";
  public static final String P1_END_MARKER = "-----END RSA PRIVATE KEY";

  public static final String P8_BEGIN_MARKER = "-----BEGIN PRIVATE KEY";
  public static final String P8_END_MARKER = "-----END PRIVATE KEY";

  public static final String EP8_BEGIN_MARKER = "-----BEGIN ENCRYPTED PRIVATE KEY";
  public static final String EP8_END_MARKER = "-----END ENCRYPTED PRIVATE KEY";

  public static final String OTHER_BEGIN_MARKER = "-----BEGIN";
  public static final String OTHER_END_MARKER = "-----END";

  private static final Map<String, Pair<PrivateKey, List<X509Certificate>>> keyCache = Collections.synchronizedMap(new HashMap<String, Pair<PrivateKey, List<X509Certificate>>>());

  @NotNull private final String myFileName;
  @NotNull private final char[] myPassword;

  PrivateKeyReader(@NotNull String fileName, @Nullable char[] password) {
    myFileName = fileName;
    myPassword = password;
  }

  @NotNull
  public PrivateKey getPrivateKey() throws IOException {
    return getPrivateKeyAndCertificate().getFirst();
  }

  @NotNull
  public Pair<PrivateKey, List<X509Certificate>> getPrivateKeyAndCertificate() throws IOException {
    Pair<PrivateKey, List<X509Certificate>> pair = keyCache.get(myFileName);
    if (pair != null) return pair;
    pair = read(myFileName, myPassword);
    keyCache.put(myFileName, pair);
    return pair;
  }

  private static Pair<PrivateKey, List<X509Certificate>> read(String fileName, @Nullable char[] password) throws IOException {
    KeyFactory factory;
    try {
      factory = KeyFactory.getInstance("RSA");
    }
    catch (NoSuchAlgorithmException e) {
      throw new IOException("JCE error: " + e.getMessage());
    }
    return readKey(factory, fileName, password);
  }

  @NotNull
  private static Pair<PrivateKey, List<X509Certificate>> readKey(KeyFactory factory, String fileName, char[] password) throws IOException {
    //noinspection IOStreamConstructor
    try (PushbackInputStream stream = new PushbackInputStream(new FileInputStream(fileName))) {
      int peeked = stream.read();
      stream.unread(peeked);
      if (peeked == 48) {
        return Pair.create(readDerKey(factory, stream, password), null);
      }
      else {
        return readPemKey(factory, stream, password);
      }
    }
  }

  @NotNull
  private static PrivateKey readDerKey(KeyFactory factory, InputStream stream, char[] password) throws IOException {
    byte[] bytes = FileUtilRt.loadBytes(stream);
    try {
      return generatePrivateKey(factory, new PKCS8EncodedKeySpec(bytes), "PKCS#8");
    }
    catch (IOException ignored2) {
      return generatePrivateKey(factory, createEncryptedKeySpec(bytes, password), "Encrypted key");
    }
  }

  private static Pair<PrivateKey, List<X509Certificate>> readPemKey(KeyFactory factory, InputStream stream, char[] password) throws IOException {
    List<String> lines = FileUtilRt.loadLines(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    PrivateKey key = null;
    List<X509Certificate> certs = new ArrayList<>();
    for (int i = 0; i < lines.size(); ) {
      Pair<PrivateKey, Integer> keyAndIdx = findPrivateKey(factory, lines, i, password);
      if (keyAndIdx != null) {
        if (key != null) {
          throw new IOException("Invalid PEM file: multiple keys found");
        }
        key = keyAndIdx.first;
        i = keyAndIdx.second;
        continue;
      }
      Pair<X509Certificate, Integer> certAndIdx = findCertAndIdx(lines, i);
      if (certAndIdx != null) {
        certs.add(certAndIdx.first);
        i = certAndIdx.second;
        continue;
      }
      ++i;
    }
    if (key != null) {
      return Pair.create(key, certs.isEmpty() ? null : certs);
    }
    throw new IOException("Invalid PEM file: no begin marker");
  }

  private static @Nullable Pair<X509Certificate, Integer> findCertAndIdx(List<String> lines, int i) throws IOException {
    if (!lines.get(i).startsWith(OTHER_BEGIN_MARKER)) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int k = i; k < lines.size(); ++k) {
      String line = lines.get(k);
      sb.append(line).append('\n');
      if (line.startsWith(OTHER_END_MARKER)) {
        try {
          return Pair.create(SslUtil.readCertificateFromString(sb.toString()), k + 1);
        }
        catch (CertificateException e) {
          throw new IOException("Error reading certificate", e);
        }
      }
    }
    return null;
  }

  private static PrivateKey generatePrivateKey(KeyFactory factory, KeySpec keySpec, String enc) throws IOException {
    try {
      return factory.generatePrivate(keySpec);
    }
    catch (InvalidKeySpecException e) {
      throw new IOException("Invalid " + enc + " PEM file: " + e.getMessage());
    }
  }


  @Nullable
  private static Pair<PrivateKey, Integer> findPrivateKey(KeyFactory factory, List<String> lines, int i, @Nullable char[] password) throws IOException {
    Pair<? extends KeySpec, Integer> keySpecAndIdx = findRSAKeySpec(lines, i);
    String enc = "PKCS#1";
    if (keySpecAndIdx == null) {
      keySpecAndIdx = findPKCS8EncodedKeySpec(lines, i);
      enc = "PKCS#8";
    }
    if (keySpecAndIdx == null) {
      keySpecAndIdx = findEncryptedKeySpec(lines, i, password);
      enc = "Encrypted key";
    }
    return keySpecAndIdx != null ? Pair.create(generatePrivateKey(factory, keySpecAndIdx.first, enc), keySpecAndIdx.second) : null;
  }

  @Nullable
  private static Pair<? extends KeySpec, Integer> findEncryptedKeySpec(List<String> lines, int i, @Nullable char[] password) throws IOException {
    if (!lines.get(i).contains(EP8_BEGIN_MARKER)) return null;
    Pair<byte[], Integer> mat = readKeyMaterial(EP8_END_MARKER, lines, i + 1);
    return Pair.create(createEncryptedKeySpec(mat.first, password), mat.second);
  }

  private static PKCS8EncodedKeySpec createEncryptedKeySpec(byte[] keyBytes, char [] password) throws IOException {
    EncryptedPrivateKeyInfo encrypted = new EncryptedPrivateKeyInfo(keyBytes);
    PBEKeySpec encryptedKeySpec = new PBEKeySpec(password);
    try {
      SecretKeyFactory pbeKeyFactory = SecretKeyFactory.getInstance(encrypted.getAlgName());
      return encrypted.getKeySpec(pbeKeyFactory.generateSecret(encryptedKeySpec));
    }
    catch (GeneralSecurityException e) {
      throw new IOException("JCE error: " + e.getMessage());
    }
  }

  @Nullable
  private static Pair<? extends KeySpec, Integer> findPKCS8EncodedKeySpec(List<String> lines, int i) throws IOException {
    if (!lines.get(i).contains(P8_BEGIN_MARKER)) return null;
    Pair<byte[], Integer> mat = readKeyMaterial(P8_END_MARKER, lines, i + 1);
    return Pair.create(new PKCS8EncodedKeySpec(mat.first), mat.second);
  }

  @Nullable
  private static Pair<? extends KeySpec, Integer> findRSAKeySpec(List<String> lines, int i) throws IOException {
    if (!lines.get(i).contains(P1_BEGIN_MARKER)) return null;
    Pair<byte[], Integer> mat = readKeyMaterial(P1_END_MARKER, lines, i + 1);
    return Pair.create(getRSAKeySpec(mat.first), mat.second);
  }

  private static Pair<byte[], Integer> readKeyMaterial(String endMarker, List<String> strings, int start) throws IOException {
    StringBuilder buf = new StringBuilder();
    for (int i = start; i < strings.size(); ++i) {
      String line = strings.get(i);
      if (line.contains(endMarker)) {
        return Pair.create(Base64.decode(buf.toString()), i + 1);
      }
      buf.append(line.trim());
    }
    throw new IOException("Invalid PEM file: No end marker");
  }

  /**
   * Convert PKCS#1 encoded private key into RSAPrivateCrtKeySpec.
   * <p/>
   * <p/>The ASN.1 syntax for the private key with CRT is
   * <p/>
   * <pre>
   * --
   * -- Representation of RSA private key with information for the CRT algorithm.
   * --
   * RSAPrivateKey ::= SEQUENCE {
   *   version           Version,
   *   modulus           INTEGER,  -- n
   *   publicExponent    INTEGER,  -- e
   *   privateExponent   INTEGER,  -- d
   *   prime1            INTEGER,  -- p
   *   prime2            INTEGER,  -- q
   *   exponent1         INTEGER,  -- d mod (p-1)
   *   exponent2         INTEGER,  -- d mod (q-1)
   *   coefficient       INTEGER,  -- (inverse of q) mod p
   *   otherPrimeInfos   OtherPrimeInfos OPTIONAL
   * }
   * </pre>
   *
   * @param keyBytes PKCS#1 encoded key
   * @return KeySpec
   */
  private static RSAPrivateCrtKeySpec getRSAKeySpec(byte[] keyBytes) throws IOException {
    DerParser parser = new DerParser(keyBytes);

    Asn1Object sequence = parser.read();
    if (sequence.getType() != DerParser.SEQUENCE) {
      throw new IOException("Invalid DER: not a sequence");
    }

    // Parse inside the sequence
    parser = sequence.getParser();

    parser.read(); // Skip version
    BigInteger modulus = parser.read().getInteger();
    BigInteger publicExp = parser.read().getInteger();
    BigInteger privateExp = parser.read().getInteger();
    BigInteger prime1 = parser.read().getInteger();
    BigInteger prime2 = parser.read().getInteger();
    BigInteger exp1 = parser.read().getInteger();
    BigInteger exp2 = parser.read().getInteger();
    BigInteger crtCoef = parser.read().getInteger();

    return new RSAPrivateCrtKeySpec(
      modulus, publicExp, privateExp, prime1, prime2,
      exp1, exp2, crtCoef);
  }
}

/**
 * A bare-minimum ASN.1 DER decoder, just having enough functions to
 * decode PKCS#1 private keys. Especially, it doesn't handle explicitly
 * tagged types with an outer tag.
 * <p/>
 * <p/>This parser can only handle one layer. To parse nested constructs,
 * get a new parser for each layer using {@code Asn1Object.getParser()}.
 * <p/>
 * <p/>There are many DER decoders in JRE but using them will tie this
 * program to a specific JCE/JVM.
 *
 * @author zhang
 */
final class DerParser {

  // Classes
  public final static int UNIVERSAL = 0x00;
  public final static int APPLICATION = 0x40;
  public final static int CONTEXT = 0x80;
  public final static int PRIVATE = 0xC0;

  // Constructed Flag
  public final static int CONSTRUCTED = 0x20;

  // Tag and data types
  public final static int ANY = 0x00;
  public final static int BOOLEAN = 0x01;
  public final static int INTEGER = 0x02;
  public final static int BIT_STRING = 0x03;
  public final static int OCTET_STRING = 0x04;
  public final static int NULL = 0x05;
  public final static int REAL = 0x09;
  public final static int ENUMERATED = 0x0a;

  public final static int SEQUENCE = 0x10;
  public final static int SET = 0x11;

  public final static int NUMERIC_STRING = 0x12;
  public final static int PRINTABLE_STRING = 0x13;
  public final static int VIDEOTEX_STRING = 0x15;
  public final static int IA5_STRING = 0x16;
  public final static int GRAPHIC_STRING = 0x19;
  public final static int ISO646_STRING = 0x1A;
  public final static int GENERAL_STRING = 0x1B;

  public final static int UTF8_STRING = 0x0C;
  public final static int UNIVERSAL_STRING = 0x1C;
  public final static int BMP_STRING = 0x1E;

  public final static int UTC_TIME = 0x17;

  private InputStream in;

  /**
   * Create a new DER decoder from an input stream.
   *
   * @param in The DER encoded stream
   */
  DerParser(InputStream in) throws IOException {
    this.in = in;
  }

  /**
   * Create a new DER decoder from a byte array.
   *
   * @param bytes The encoded bytes
   */
  DerParser(byte[] bytes) throws IOException {
    this(new ByteArrayInputStream(bytes));
  }

  /**
   * Read next object. If it's constructed, the value holds
   * encoded content and it should be parsed by a new
   * parser from {@code Asn1Object.getParser}.
   *
   * @return A object
   */
  public Asn1Object read() throws IOException {
    int tag = in.read();

    if (tag == -1) {
      throw new IOException("Invalid DER: stream too short, missing tag");
    }

    int length = getLength();

    byte[] value = new byte[length];
    int n = in.read(value);
    if (n < length) {
      throw new IOException("Invalid DER: stream too short, missing value");
    }

    return new Asn1Object(tag, length, value);
  }

  /**
   * Decode the length of the field. Can only support length
   * encoding up to 4 octets.
   * <p/>
   * <p/>In BER/DER encoding, length can be encoded in 2 forms,
   * <ul>
   * <li>Short form. One octet. Bit 8 has value "0" and bits 7-1
   * give the length.
   * <li>Long form. Two to 127 octets (only 4 is supported here).
   * Bit 8 of first octet has value "1" and bits 7-1 give the
   * number of additional length octets. Second and following
   * octets give the length, base 256, most significant digit first.
   * </ul>
   *
   * @return The length as integer
   */
  private int getLength() throws IOException {

    int i = in.read();
    if (i == -1) {
      throw new IOException("Invalid DER: length missing");
    }

    // A single byte short length
    if ((i & ~0x7F) == 0) {
      return i;
    }

    int num = i & 0x7F;

    // We can't handle length longer than 4 bytes
    if (i >= 0xFF || num > 4) {
      throw new IOException("Invalid DER: length field too big ("
                            + i + ")");
    }

    byte[] bytes = new byte[num];
    int n = in.read(bytes);
    if (n < num) {
      throw new IOException("Invalid DER: length too short");
    }

    return new BigInteger(1, bytes).intValue();
  }
}


/**
 * An ASN.1 TLV. The object is not parsed. It can
 * only handle integers and strings.
 *
 * @author zhang
 */
final class Asn1Object {

  private final int type;
  private final int length;
  private final byte[] value;
  private final int tag;

  /**
   * Construct a ASN.1 TLV. The TLV could be either a
   * constructed or primitive entity.
   * <p/>
   * <p/>The first byte in DER encoding is made of following fields,
   * <pre>
   * -------------------------------------------------
   * |Bit 8|Bit 7|Bit 6|Bit 5|Bit 4|Bit 3|Bit 2|Bit 1|
   * -------------------------------------------------
   * |  Class    | CF  |     +      Type             |
   * -------------------------------------------------
   * </pre>
   * <ul>
   * <li>Class: Universal, Application, Context or Private
   * <li>CF: Constructed flag. If 1, the field is constructed.
   * <li>Type: This is actually called tag in ASN.1. It
   * indicates data type (Integer, String) or a construct
   * (sequence, choice, set).
   * </ul>
   *
   * @param tag    Tag or Identifier
   * @param length Length of the field
   * @param value  Encoded octet string for the field.
   */
  Asn1Object(int tag, int length, byte[] value) {
    this.tag = tag;
    this.type = tag & 0x1F;
    this.length = length;
    this.value = value;
  }

  public int getType() {
    return type;
  }

  public int getLength() {
    return length;
  }

  public byte[] getValue() {
    return value;
  }

  public boolean isConstructed() {
    return (tag & DerParser.CONSTRUCTED) == DerParser.CONSTRUCTED;
  }

  /**
   * For constructed field, return a parser for its content.
   *
   * @return A parser for the construct.
   */
  public DerParser getParser() throws IOException {
    if (!isConstructed()) {
      throw new IOException("Invalid DER: can't parse primitive entity");
    }

    return new DerParser(value);
  }

  /**
   * Get the value as integer
   *
   * @return BigInteger
   */
  public BigInteger getInteger() throws IOException {
    if (type != DerParser.INTEGER) {
      throw new IOException("Invalid DER: object is not integer");
    }

    return new BigInteger(value);
  }

  /**
   * Get value as string. Most strings are treated
   * as Latin-1.
   *
   * @return Java string
   */
  public String getString() throws IOException {

    String encoding;

    switch (type) {

      // Not all are Latin-1 but it's the closest thing
      case DerParser.NUMERIC_STRING:
      case DerParser.PRINTABLE_STRING:
      case DerParser.VIDEOTEX_STRING:
      case DerParser.IA5_STRING:
      case DerParser.GRAPHIC_STRING:
      case DerParser.ISO646_STRING:
      case DerParser.GENERAL_STRING:
        encoding = "ISO-8859-1";
        break;

      case DerParser.BMP_STRING:
        encoding = "UTF-16BE";
        break;

      case DerParser.UTF8_STRING:
        encoding = "UTF-8";
        break;

      case DerParser.UNIVERSAL_STRING:
        throw new IOException("Invalid DER: can't handle UCS-4 string");

      default:
        throw new IOException("Invalid DER: object is not a string");
    }

    return new String(value, encoding);
  }
}
