package com.intellij.jps.cache.client;

import java.util.Objects;

public class ArtifactoryEntryDto {
  private String repo;
  private String path;
  private String name;
  private String type;
  private Integer size;

  public String getRepo() {
    return repo;
  }

  public void setRepo(String repo) {
    this.repo = repo;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ArtifactoryEntryDto dto = (ArtifactoryEntryDto)o;
    return Objects.equals(repo, dto.repo) &&
           Objects.equals(path, dto.path) &&
           Objects.equals(name, dto.name) &&
           Objects.equals(type, dto.type) &&
           Objects.equals(size, dto.size);
  }

  @Override
  public int hashCode() {
    return Objects.hash(repo, path, name, type, size);
  }
}
