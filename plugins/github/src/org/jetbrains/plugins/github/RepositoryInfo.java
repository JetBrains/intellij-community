package org.jetbrains.plugins.github;

import com.intellij.openapi.util.Comparing;
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

  public boolean isFork() {
    return Boolean.valueOf(myRepository.getChildText("fork"));
  }

  public String getParent() {
    return myRepository.getChildText("parent");
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof RepositoryInfo)){
      return false;
    }
    final RepositoryInfo repositoryInfo = (RepositoryInfo)obj;
    return Comparing.equal(getName(), repositoryInfo.getName()) &&
           Comparing.equal(getOwner(), repositoryInfo.getOwner());
  }
}
