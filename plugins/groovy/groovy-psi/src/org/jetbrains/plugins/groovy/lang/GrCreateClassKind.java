package org.jetbrains.plugins.groovy.lang;

/**
 * Created by Max Medvedev on 28/05/14
 */
public enum GrCreateClassKind {
  CLASS     ("class"),
  INTERFACE ("interface"),
  TRAIT     ("trait"),
  ENUM      ("enum"),
  ANNOTATION("annotation");

  private final String myDescription;

  GrCreateClassKind(final String description) {
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }
}
