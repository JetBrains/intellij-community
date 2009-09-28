package org.jetbrains.plugins.groovy.doc;

/**
 * @author Dmitry Krasilschikov
 */
public class GroovyDocConfiguration {
  public String OUTPUT_DIRECTORY = "";
  public String INPUT_DIRECTORY = "";
  public String WINDOW_TITLE = "";
  public String[] PACKAGES = new String[]{ALL_PACKAGES};

  public boolean OPTION_IS_USE = true;
  public boolean OPTION_IS_PRIVATE = true;
  public boolean OPEN_IN_BROWSER = true;

  public static final String ALL_PACKAGES = "**.*";
}
