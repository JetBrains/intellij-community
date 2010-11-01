package org.jetbrains.plugins.github;

/**
* @author oleg
* @date 10/21/10
*/
public class RepositoryInfo {
  public RepositoryInfo(final String name, final String description, final String url) {
    myName = name;
    myDescription = description;
    myUrl = url;
  }

  private String myName;
  private String myDescription;
  private String myUrl;

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getUrl() {
    return myUrl;
  }
}
