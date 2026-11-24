// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The check of an Authenticode signature through {@code WinVerifyTrust} downcalls into {@code wintrust.dll}.
 * Windows only: the first call loads the library.
 * <p>
 * Each layout is for a 64-bit target, the same for x64 and ARM64. A layout names only the fields that the check
 * reads or writes. A padding member holds the place of every other field. {@code WindowsTrustLayoutTest} pins each
 * size and each offset. A layout needs no library, so a test can read it on any operating system.
 */
@ApiStatus.Internal
public final class WindowsTrust {
  private WindowsTrust() { }

  /**
   * The result of a check.
   *
   * @param status            the answer of {@code WinVerifyTrust}: 0 for a valid signature, else an {@code HRESULT}
   *                          such as {@code TRUST_E_NOSIGNATURE} ({@code 0x800B0100})
   * @param signerCertificate the DER encoding of the certificate of the signer, or {@code null}. It is not
   *                          {@code null} only for the status 0.
   */
  public record Signature(int status, byte @Nullable [] signerCertificate) { }

  /** {@code WINTRUST_FILE_INFO { DWORD cbStruct; LPCWSTR pcwszFilePath; HANDLE hFile; GUID* pgKnownSubject; }}, 32 bytes */
  public static final StructLayout WINTRUST_FILE_INFO = MemoryLayout.structLayout(
    JAVA_INT.withName("cbStruct"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("pcwszFilePath"),
    MemoryLayout.paddingLayout(16));

  public static final long FILE_INFO_FILE_PATH = WINTRUST_FILE_INFO.byteOffset(groupElement("pcwszFilePath"));

  /**
   * {@code WINTRUST_DATA} from {@code wintrust.h}, 88 bytes. The padding holds {@code pPolicyCallbackData},
   * {@code pSIPClientData}, {@code pwszURLReference}, {@code dwUIContext} and {@code pSignatureSettings}.
   */
  public static final StructLayout WINTRUST_DATA = MemoryLayout.structLayout(
    JAVA_INT.withName("cbStruct"),
    MemoryLayout.paddingLayout(20),
    JAVA_INT.withName("dwUIChoice"),
    JAVA_INT.withName("fdwRevocationChecks"),
    JAVA_INT.withName("dwUnionChoice"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("pFile"),
    JAVA_INT.withName("dwStateAction"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("hWVTStateData"),
    MemoryLayout.paddingLayout(8),
    JAVA_INT.withName("dwProvFlags"),
    MemoryLayout.paddingLayout(12));

  public static final long DATA_UI_CHOICE = WINTRUST_DATA.byteOffset(groupElement("dwUIChoice"));
  public static final long DATA_REVOCATION_CHECKS = WINTRUST_DATA.byteOffset(groupElement("fdwRevocationChecks"));
  public static final long DATA_UNION_CHOICE = WINTRUST_DATA.byteOffset(groupElement("dwUnionChoice"));
  public static final long DATA_FILE = WINTRUST_DATA.byteOffset(groupElement("pFile"));
  public static final long DATA_STATE_ACTION = WINTRUST_DATA.byteOffset(groupElement("dwStateAction"));
  public static final long DATA_STATE_DATA = WINTRUST_DATA.byteOffset(groupElement("hWVTStateData"));
  public static final long DATA_PROV_FLAGS = WINTRUST_DATA.byteOffset(groupElement("dwProvFlags"));

  /** The first two fields of {@code CRYPT_PROVIDER_CERT { DWORD cbStruct; PCCERT_CONTEXT pCert; ... }} */
  public static final StructLayout CRYPT_PROVIDER_CERT = MemoryLayout.structLayout(
    JAVA_INT.withName("cbStruct"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("pCert"));

  public static final long PROVIDER_CERT_CERT = CRYPT_PROVIDER_CERT.byteOffset(groupElement("pCert"));

  /**
   * {@code CERT_CONTEXT { DWORD dwCertEncodingType; BYTE* pbCertEncoded; DWORD cbCertEncoded; PCERT_INFO pCertInfo;
   * HCERTSTORE hCertStore; }}, 40 bytes
   */
  public static final StructLayout CERT_CONTEXT = MemoryLayout.structLayout(
    MemoryLayout.paddingLayout(8),
    ADDRESS.withName("pbCertEncoded"),
    JAVA_INT.withName("cbCertEncoded"),
    MemoryLayout.paddingLayout(20));

  public static final long CERT_CONTEXT_ENCODED = CERT_CONTEXT.byteOffset(groupElement("pbCertEncoded"));
  public static final long CERT_CONTEXT_ENCODED_SIZE = CERT_CONTEXT.byteOffset(groupElement("cbCertEncoded"));

  /** {@code WINTRUST_ACTION_GENERIC_VERIFY_V2}: the Authenticode policy */
  private static final UUID GENERIC_VERIFY_V2 = UUID.fromString("00AAC56B-CD44-11D0-8CC2-00C04FC295EE");

  private static final int WTD_UI_NONE = 2;
  private static final int WTD_REVOKE_NONE = 0;
  private static final int WTD_CHOICE_FILE = 1;
  private static final int WTD_STATEACTION_VERIFY = 1;
  private static final int WTD_STATEACTION_CLOSE = 2;
  private static final int WTD_REVOCATION_CHECK_NONE = 0x10;

  /**
   * Verifies the signature that is inside {@code file}, with no window and no revocation check.
   * Windows verifies the hash of the whole file and the certificate chain of the signer.
   * <p>
   * The certificate of the result is the signer of the chain that Windows verified. It is not a certificate from the
   * certificate list of the file, because that list is not signed.
   */
  public static @NotNull Signature verifyEmbeddedSignature(@NotNull Path file) {
    try (var arena = Arena.ofConfined()) {
      var fileInfo = arena.allocate(WINTRUST_FILE_INFO);
      fileInfo.set(JAVA_INT, 0, (int)WINTRUST_FILE_INFO.byteSize());
      fileInfo.set(ADDRESS, FILE_INFO_FILE_PATH, arena.allocateFrom(file.toAbsolutePath().toString(), StandardCharsets.UTF_16LE));

      var data = arena.allocate(WINTRUST_DATA);
      data.set(JAVA_INT, 0, (int)WINTRUST_DATA.byteSize());
      data.set(JAVA_INT, DATA_UI_CHOICE, WTD_UI_NONE);
      data.set(JAVA_INT, DATA_REVOCATION_CHECKS, WTD_REVOKE_NONE);
      data.set(JAVA_INT, DATA_UNION_CHOICE, WTD_CHOICE_FILE);
      data.set(ADDRESS, DATA_FILE, fileInfo);
      data.set(JAVA_INT, DATA_STATE_ACTION, WTD_STATEACTION_VERIFY);
      data.set(JAVA_INT, DATA_PROV_FLAGS, WTD_REVOCATION_CHECK_NONE);

      var action = WindowsCom.guid(arena, GENERIC_VERIFY_V2);
      // INVALID_HANDLE_VALUE as the window: no interactive user
      var noWindow = MemorySegment.ofAddress(WindowsKernel32.INVALID_HANDLE_VALUE);
      var status = (int)Handles.WIN_VERIFY_TRUST.invokeExact(noWindow, action, data);
      try {
        return new Signature(status, status == 0 ? signerCertificate(data.get(ADDRESS, DATA_STATE_DATA)) : null);
      }
      finally {
        // the verify action allocates the state data, and only the close action releases it
        data.set(JAVA_INT, DATA_STATE_ACTION, WTD_STATEACTION_CLOSE);
        var ignored = (int)Handles.WIN_VERIFY_TRUST.invokeExact(noWindow, action, data);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return a copy of the encoded certificate of the first signer of the verified chain, or {@code null} */
  private static byte @Nullable [] signerCertificate(@NotNull MemorySegment stateData) throws Throwable {
    if (stateData.address() == 0) {
      return null;
    }
    var providerData = (MemorySegment)Handles.PROV_DATA_FROM_STATE_DATA.invokeExact(stateData);
    if (providerData.address() == 0) {
      return null;
    }
    var signer = (MemorySegment)Handles.GET_PROV_SIGNER_FROM_CHAIN.invokeExact(providerData, 0, 0, 0);
    if (signer.address() == 0) {
      return null;
    }
    var providerCert = (MemorySegment)Handles.GET_PROV_CERT_FROM_CHAIN.invokeExact(signer, 0);
    if (providerCert.address() == 0) {
      return null;
    }
    var certContext = providerCert.reinterpret(CRYPT_PROVIDER_CERT.byteSize()).get(ADDRESS, PROVIDER_CERT_CERT);
    if (certContext.address() == 0) {
      return null;
    }
    var context = certContext.reinterpret(CERT_CONTEXT.byteSize());
    var encoded = context.get(ADDRESS, CERT_CONTEXT_ENCODED);
    var size = context.get(JAVA_INT, CERT_CONTEXT_ENCODED_SIZE);
    if (encoded.address() == 0 || size <= 0) {
      return null;
    }
    return encoded.reinterpret(size).toArray(JAVA_BYTE);
  }

  /** The library and the handles load on the first call, so the layouts stay readable on another operating system. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup WINTRUST = WindowsSystemLibraries.lookup("wintrust.dll");

    /** {@code LONG WinVerifyTrust(HWND window, GUID* action, LPVOID data)} */
    static final MethodHandle WIN_VERIFY_TRUST = LINKER.downcallHandle(
      WINTRUST.findOrThrow("WinVerifyTrust"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /** {@code CRYPT_PROVIDER_DATA* WTHelperProvDataFromStateData(HANDLE stateData)} */
    static final MethodHandle PROV_DATA_FROM_STATE_DATA = LINKER.downcallHandle(
      WINTRUST.findOrThrow("WTHelperProvDataFromStateData"), FunctionDescriptor.of(ADDRESS, ADDRESS));

    /**
     * {@code CRYPT_PROVIDER_SGNR* WTHelperGetProvSignerFromChain(CRYPT_PROVIDER_DATA* data, DWORD signerIndex,
     * BOOL counterSigner, DWORD counterSignerIndex)}
     */
    static final MethodHandle GET_PROV_SIGNER_FROM_CHAIN = LINKER.downcallHandle(
      WINTRUST.findOrThrow("WTHelperGetProvSignerFromChain"), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

    /** {@code CRYPT_PROVIDER_CERT* WTHelperGetProvCertFromChain(CRYPT_PROVIDER_SGNR* signer, DWORD certIndex)} */
    static final MethodHandle GET_PROV_CERT_FROM_CHAIN = LINKER.downcallHandle(
      WINTRUST.findOrThrow("WTHelperGetProvCertFromChain"), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
  }
}
