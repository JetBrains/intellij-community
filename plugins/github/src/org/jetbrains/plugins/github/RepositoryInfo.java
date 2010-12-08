package org.jetbrains.plugins.github;

import org.jdom.Element;

/**
* @author oleg
* @date 10/21/10
*/
public class RepositoryInfo {
  private final Element myRepository;

  public RepositoryInfo(final Element repository) {
    myRepository = repository;
  }

  public String getName() {
    return myRepository.getChildText("name");
  }

  public String getOwner() {
    return myRepository.getChildText("owner");
  }
}
