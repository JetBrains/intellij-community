package org.jetbrains.plugins.github;

/**
* @author oleg
* @date 10/21/10
*/
public class RepositoryInfo {
  public RepositoryInfo(final String name, final String owner) {
    myName = name;
    myOwner = owner;
  }

  private String myName;
  private String myOwner;

  public String getName() {
    return myName;
  }

  public String getOwner() {
    return myOwner;
  }
}
