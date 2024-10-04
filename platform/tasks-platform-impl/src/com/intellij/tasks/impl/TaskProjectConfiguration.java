// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
* @author Dmitry Avdeev
*/
@Service(Service.Level.PROJECT)
@State(name = "TaskProjectConfiguration")
@ApiStatus.Internal
public final class TaskProjectConfiguration implements PersistentStateComponent<TaskProjectConfiguration> {
  @Tag("server")
  public static class SharedServer {
    @Attribute("type")
    public String type;
    @Attribute("url")
    public String url;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SharedServer server = (SharedServer)o;

      if (type != null ? !type.equals(server.type) : server.type != null) return false;
      if (url != null ? !url.equals(server.url) : server.url != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = type != null ? type.hashCode() : 0;
      result = 31 * result + (url != null ? url.hashCode() : 0);
      return result;
    }
  }

  @Property(surroundWithTag = false)
  @XCollection(elementName = "server")
  public List<SharedServer> servers = new ArrayList<>();

  private final Project myProject;

  // for serialization
  @NonInjectable
  public TaskProjectConfiguration() {
    myProject = null;
  }

  public TaskProjectConfiguration(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public TaskProjectConfiguration getState() {
    LinkedHashSet<SharedServer> set = new LinkedHashSet<>(this.servers);
    for (TaskRepository repository : TaskManager.getManager(myProject).getAllRepositories()) {
      if (repository.isShared()) {
        SharedServer server = new SharedServer();
        server.type = repository.getRepositoryType().getName();
        server.url = repository.getUrl();
        set.add(server);
      }
    }
    servers.clear();
    servers.addAll(set);
    return this;
  }

  @Override
  public void loadState(@NotNull TaskProjectConfiguration state) {
    servers.clear();
    for (final SharedServer server : state.servers) {
      if (server.url == null || server.type == null) {
        continue;
      }
      servers.add(server);
    }
  }

}
