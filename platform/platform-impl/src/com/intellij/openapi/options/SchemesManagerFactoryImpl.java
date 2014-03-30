/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceBean;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SchemesManagerFactoryImpl extends SchemesManagerFactory implements SettingsSavingComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.SchemesManagerFactoryImpl");

  private final List<SchemesManagerImpl> myRegisteredManagers = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public <T extends Scheme, E extends ExternalizableScheme> SchemesManager<T, E> createSchemesManager(final String fileSpec,
                                                                                                      final SchemeProcessor<E> processor,
                                                                                                      final RoamingType roamingType) {
    final Application application = ApplicationManager.getApplication();
    if (!(application instanceof ApplicationImpl)) return null;
    String baseDirPath = ((ApplicationImpl)application).getStateStore().getStateStorageManager().expandMacros(fileSpec);

    if (baseDirPath != null) {
      StreamProvider provider = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProvider();
      SchemesManagerImpl<T, E> manager = new SchemesManagerImpl<T, E>(fileSpec, processor, roamingType, provider, new File(baseDirPath));
      myRegisteredManagers.add(manager);
      return manager;
    }
    else {
      return new AbstractSchemesManager<T, E>() {
        @Override
        @NotNull
        public Collection<E> loadSchemes() {
          return Collections.emptyList();
        }

        @Override
        @NotNull
        public Collection<SharedScheme<E>> loadSharedSchemes(final Collection<T> currentSchemeList) {
          return Collections.emptyList();
        }

        @Override
        public void exportScheme(@NotNull final E scheme, final String name, final String description) {
        }

        @Override
        public boolean isImportAvailable() {
          return false;
        }

        @Override
        public boolean isShared(final Scheme scheme) {
          return false;
        }

        @Override
        public void save() {
        }

        @Override
        protected void onSchemeDeleted(final Scheme toDelete) {
        }

        @Override
        protected void onSchemeAdded(final T scheme) {
        }

        @Override
        public boolean isExportAvailable() {
          return false;
        }

        @Override
        public File getRootDirectory() {
          return null;
        }
      };
    }
  }

  @Override
  public void updateConfigFilesFromStreamProviders() {
    ServiceBean.loadServicesFromBeans(SCHEME_OWNER, Object.class);
    for (SchemesManagerImpl registeredManager : myRegisteredManagers) {
      try {
        registeredManager.updateConfigFilesFromStreamProviders();
      }
      catch (Throwable e) {
        LOG.info("Cannot save settings for " + registeredManager.getClass().getName(), e);
      }
    }
  }

  @Override
  public void save() {
    ServiceBean.loadServicesFromBeans(SCHEME_OWNER, Object.class);
    for (SchemesManager registeredManager : myRegisteredManagers) {
      try {
        registeredManager.save();
      }
      catch (Throwable e) {
        LOG.info("Cannot save settings for " + registeredManager.getClass().getName(), e);
      }
    }
  }
}
