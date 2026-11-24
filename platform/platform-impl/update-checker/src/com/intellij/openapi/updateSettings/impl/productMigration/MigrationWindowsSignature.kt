// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.WindowsTrust
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.naming.InvalidNameException
import javax.naming.ldap.LdapName
import javax.security.auth.x500.X500Principal

private val LOG = logger<WindowsPublisher>()

private const val ORGANIZATION = "O"
private const val COUNTRY = "C"

/**
 * The publisher of a signed Windows file: the `O` and the `C` of the subject of the signer certificate.
 * A certificate authority validates these two fields. They stay the same when the publisher gets a new certificate.
 */
@ApiStatus.Internal
data class WindowsPublisher(val organization: String, val country: String)

/** Returns the publisher of the certificate subject [subject], or `null` unless the name has exactly one `O` and one `C`. */
@ApiStatus.Internal
fun windowsPublisher(subject: X500Principal): WindowsPublisher? {
  val name = try {
    LdapName(subject.getName(X500Principal.RFC2253))
  }
  catch (_: InvalidNameException) {
    return null
  }

  val organizations = ArrayList<String>()
  val countries = ArrayList<String>()
  for (rdn in name.rdns) {
    for (attribute in rdn.toAttributes().all) {
      val values = when {
        attribute.id.equals(ORGANIZATION, ignoreCase = true) -> organizations
        attribute.id.equals(COUNTRY, ignoreCase = true) -> countries
        else -> continue
      }
      for (value in attribute.all) {
        // a value that is not a string has no text to compare
        values.add(value as? String ?: return null)
      }
    }
  }
  val organization = organizations.singleOrNull()?.takeIf { it.isNotBlank() } ?: return null
  val country = countries.singleOrNull()?.takeIf { it.isNotBlank() } ?: return null
  return WindowsPublisher(organization, country)
}

/**
 * Returns `false` when [file] has no valid Authenticode signature of the publisher of the running IDE.
 * Throws the usual install error when the executable file of the running process has no valid signature.
 * No publisher name is in the code: [runningPublisher] gives the expected one.
 */
@ApiStatus.Internal
fun isTrustedWindowsFile(
  file: Path,
  allowUnsigned: Boolean = System.getProperty(ALLOW_UNSIGNED_PROPERTY).toBoolean(),
  runningPublisher: () -> WindowsPublisher? = { runningExecutable()?.let(::readWindowsPublisher) },
  filePublisher: (Path) -> WindowsPublisher? = ::readWindowsPublisher,
): Boolean {
  if (allowUnsigned) {
    LOG.info("the signature check of ${file} is off: -D${ALLOW_UNSIGNED_PROPERTY}=true")
    return true
  }
  val expected = runningPublisher()
                 ?: throw installError("the running IDE has no valid signature, so the signature of ${file} cannot be checked")
  val publisher = filePublisher(file) ?: return false
  if (publisher != expected) {
    LOG.info("${file} has the publisher ${publisher}, expected ${expected}")
    return false
  }
  return true
}

/** Returns the publisher of the valid signature of [file], or `null` for no valid signature. Windows verifies the whole file. */
private fun readWindowsPublisher(file: Path): WindowsPublisher? {
  val signature = WindowsTrust.verifyEmbeddedSignature(file)
  if (signature.status != 0) {
    LOG.info("${file} has no valid signature: WinVerifyTrust gives 0x${Integer.toHexString(signature.status)}")
    return null
  }
  val encoded = signature.signerCertificate ?: run {
    LOG.info("Windows gives no signer certificate for ${file}")
    return null
  }
  val subject = try {
    (CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate).subjectX500Principal
  }
  catch (e: CertificateException) {
    LOG.info("cannot read the signer certificate of ${file}: ${e.message}")
    return null
  }
  return windowsPublisher(subject) ?: null.also { LOG.info("the signer of ${file} has no single O and C: ${subject}") }
}

/** In an installed IDE, this is the launcher. In a run from the sources, it is `java.exe` of the runtime. */
private fun runningExecutable(): Path? {
  val command = ProcessHandle.current().info().command().orElse(null)
  if (command == null) {
    LOG.info("the executable file of the running process is unknown")
    return null
  }
  return try {
    Path.of(command)
  }
  catch (_: InvalidPathException) {
    LOG.info("invalid path of the running process: ${command}")
    null
  }
}
