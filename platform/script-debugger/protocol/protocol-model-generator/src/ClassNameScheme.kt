package org.jetbrains.protocolModelGenerator

fun getPackageName(rootPackage: String, domain: String): String {
  if (domain.isEmpty()) {
    return rootPackage
  }
  return "$rootPackage.${domain.toLowerCase()}"
}

abstract class ClassNameScheme(private val suffix: String, private val rootPackage: String) {
  fun getFullName(domainName: String, baseName: String): NamePath {
    return NamePath(getShortName(baseName), NamePath(getPackageNameVirtual(domainName)))
  }

  fun getShortName(baseName: String): String {
    return if (baseName.endsWith("Descriptor")) baseName else String(getShortNameChars(baseName))
  }

  private fun getShortNameChars(baseName: String): CharArray {
    val name = CharArray(baseName.length + suffix.length)
    baseName.toCharArray(name, 0, 0, baseName.length)
    if (!suffix.isEmpty()) {
      suffix.toCharArray(name, baseName.length, 0, suffix.length)
    }
    if (Character.isLowerCase(name[0])) {
      name[0] = Character.toUpperCase(name[0])
    }
    if (baseName.endsWith("breakpoint")) {
      name[baseName.length - "breakpoint".length] = 'B'
    }
    else if (baseName.endsWith("breakpoints")) {
      name[baseName.length - "breakpoints".length] = 'B'
    }
    return name
  }

  fun getPackageNameVirtual(domainName: String): String = getPackageName(rootPackage, domainName)

  class Input(suffix: String, rootPackage: String) : ClassNameScheme(suffix, rootPackage) {
    fun getParseMethodName(domain: String, name: String): String {
      return "read" + capitalizeFirstChar(domain) + getShortName(name)
    }
  }

  class Output(suffix: String, rootPackage: String) : ClassNameScheme(suffix, rootPackage)

  class Common(suffix: String, rootPackage: String) : ClassNameScheme(suffix, rootPackage)
}