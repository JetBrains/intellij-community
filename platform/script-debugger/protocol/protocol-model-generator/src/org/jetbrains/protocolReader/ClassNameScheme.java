package org.jetbrains.protocolReader;

abstract class ClassNameScheme {
  private final String suffix;
  private final String rootPackage;

  private ClassNameScheme(String suffix, String rootPackage) {
    this.suffix = suffix;
    this.rootPackage = rootPackage;
  }

  NamePath getFullName(String domainName, String baseName) {
    return new NamePath(getShortName(baseName), new NamePath(getPackageNameVirtual(domainName)));
  }

  String getShortName(String baseName) {
    return new String(getShortNameChars(baseName));
  }

  char[] getShortNameChars(String baseName) {
    char[] name = new char[baseName.length() + suffix.length()];
    baseName.getChars(0, baseName.length(), name, 0);
    if (!suffix.isEmpty()) {
      suffix.getChars(0, suffix.length(), name, baseName.length());
    }
    if (Character.isLowerCase(name[0])) {
      name[0] = Character.toUpperCase(name[0]);
    }
    if (baseName.endsWith("breakpoint")) {
      name[baseName.length() - "breakpoint".length()] = 'B';
    }
    else if (baseName.endsWith("breakpoints")) {
      name[baseName.length() - "breakpoints".length()] = 'B';
    }
    return name;
  }

  protected String getPackageNameVirtual(String domainName) {
    return getPackageName(rootPackage, domainName);
  }

  public static String getPackageName(String rootPackage, String domain) {
    if (domain.isEmpty()) {
      return rootPackage;
    }
    return rootPackage + '.' + domain.toLowerCase();
  }

  static class Input extends ClassNameScheme {
    Input(String suffix, String rootPackage) {
      super(suffix, rootPackage);
    }

    String getParseMethodName(String domain, String name) {
      return "read" + Generator.capitalizeFirstChar(domain) + getShortName(name);
    }
  }

  static class Output extends ClassNameScheme {
    Output(String suffix, String rootPackage) {
      super(suffix, rootPackage);
    }
  }

  static class Common extends ClassNameScheme {
    Common(String suffix, String rootPackage) {
      super(suffix, rootPackage);
    }
  }
}