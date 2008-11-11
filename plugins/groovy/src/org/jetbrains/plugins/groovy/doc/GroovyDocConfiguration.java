package org.jetbrains.plugins.groovy.doc;

/**
 * @author Dmitry Krasilschikov
 */
public class GroovyDocConfiguration {
  public String OUTPUT_DIRECTORY;
  public String INPUT_DIRECTORY;
  public String WINDOW_TITLE;
  public String[] PACKAGES;

  public boolean OPTION_IS_USE;
  public boolean OPTION_IS_PRIVATE;
  public boolean OPEN_IN_BROWSER;
  public static final String ALL_PACKAGES = "**.*";

  public GroovyDocConfiguration() {
    OUTPUT_DIRECTORY = "";
    INPUT_DIRECTORY = "";
    WINDOW_TITLE = "";

    PACKAGES = new String[]{ALL_PACKAGES};

    OPEN_IN_BROWSER = true;
    OPTION_IS_USE = true;
    OPTION_IS_PRIVATE = true;
  }

  //public GroovyDocConfigurable createConfigurable() {
  //  return new GroovyDocConfigurable(this);
  //}
}
