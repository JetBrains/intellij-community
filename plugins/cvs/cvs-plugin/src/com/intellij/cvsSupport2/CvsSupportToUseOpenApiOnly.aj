package com.intellij.cvsSupport2;

public aspect CvsSupportToUseOpenApiOnly {
  /*
  pointcut useOfIntellijApi() : call(* com.intellij..*.*(..)) || call (com.intellij..*.new(..));

  pointcut useOfOpenApi() : call(* com.intellij.openapi.*.*.*(..)) || call (com.intellij.openapi..*.new(..));

  pointcut useOfCvsSupportItself() : call(* com.intellij.cvsSupport2..*.*(..)) || call (com.intellij.cvsSupport2..*.new(..));

  pointcut insideCvsSupport() : within(com.intellij.cvsSupport2..*);

  declare warning : useOfIntellijApi() && !useOfOpenApi() && !useOfCvsSupportItself() && insideCvsSupport()
    : "Cvs support plugin should use only OpenApi";
  */
}