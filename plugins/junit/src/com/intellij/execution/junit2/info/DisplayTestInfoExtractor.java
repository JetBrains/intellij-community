package com.intellij.execution.junit2.info;

public interface DisplayTestInfoExtractor {
  String getComment(PsiClassLocator classLocator);
  String getName(PsiClassLocator classLocator);

  DisplayTestInfoExtractor FOR_CLASS = new DisplayTestInfoExtractor() {
    public String getComment(final PsiClassLocator classLocator) {
      return classLocator.getPackage();
    }

    public String getName(final PsiClassLocator classLocator) {
      return classLocator.getName();
    }
  };

  DisplayTestInfoExtractor CLASS_FULL_NAME = new DisplayTestInfoExtractor() {
    public String getComment(final PsiClassLocator classLocator) {
      return classLocator.getQualifiedName();
    }

    public String getName(final PsiClassLocator classLocator) {
      return null;
    }
  };
}
