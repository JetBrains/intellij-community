/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.checkout.branches;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The branch configuration wrapper
 */
public class GitBranchConfiguration {
  /**
   * The configuration
   */
  final GitBranchConfigurations myConfig;
  /**
   * The name of configuration
   */
  private String myName;
  /**
   * The auto-detected flag
   */
  private boolean myAutoDetected;
  /**
   * The root to reference mapping
   */
  private HashMap<String, String> myReferences = new HashMap<String, String>();
  /**
   * The
   */
  @Nullable private GitBranchConfigurations.BranchChanges myChanges;

  /**
   * The configuration with the specified name
   *
   * @param config
   * @param name
   */
  GitBranchConfiguration(GitBranchConfigurations config, String name) {
    myConfig = config;
    myName = name;
  }

  /**
   * @return the name of configuration
   */
  public String getName() {
    synchronized (myConfig.getStateLock()) {
      return myName;
    }
  }

  /**
   * @return the name of configuration
   */
  public void setName(String name) {
    synchronized (myConfig.getStateLock()) {
      assert name != null;
      if (name.equals(myName)) {
        return;
      }
      assert myConfig.configurationRenamed(this, myName, name) : "Configuration should have existed";
      myName = name;
    }
  }


  /**
   * @return the copy of list of branches for configuration
   */
  public Map<String, String> getReferences() {
    synchronized (myConfig.getStateLock()) {
      return new HashMap<String, String>(myReferences);
    }
  }

  public void setBranch(String root, String reference) {
    synchronized (myConfig.getStateLock()) {
      myReferences.put(root, reference);
    }
  }

  public String getReference(String root) {
    synchronized (myConfig.getStateLock()) {
      return myReferences.get(root);
    }
  }

  public void clearReferences() {
    synchronized (myConfig.getStateLock()) {
      myReferences.clear();
    }
  }


  /**
   * Set changes to the configuration. Note that changes are assumed not to change
   *
   * @param changes
   */
  void setChanges(@Nullable GitBranchConfigurations.BranchChanges changes) {
    synchronized (myConfig.getStateLock()) {
      myChanges = changes;
    }
  }

  /**
   * @return get copy of stored changes descriptor
   */
  @Nullable
  GitBranchConfigurations.BranchChanges getChanges() {
    synchronized (myConfig.getStateLock()) {
      if (myChanges == null) {
        return null;
      }
      GitBranchConfigurations.BranchChanges rc = new GitBranchConfigurations.BranchChanges();
      XmlSerializerUtil.copyBean(myChanges, rc);
      return rc;
    }
  }


  /**
   * Set mapping for roots
   *
   * @param references the new mapping
   */
  public void setReferences(Map<String, String> references) {
    synchronized (myConfig.getStateLock()) {
      myReferences.clear();
      myReferences.putAll(references);
    }
  }

  /**
   * @return true if the configuration was auto-detected
   */
  public boolean isAutoDetected() {
    synchronized (myConfig.getStateLock()) {
      return myAutoDetected;
    }
  }

  /**
   * Indicate if configuration was auto-detected
   *
   * @param autoDetected new value
   */
  public void setAutoDetected(boolean autoDetected) {
    synchronized (myConfig.getStateLock()) {
      myAutoDetected = autoDetected;
    }
  }
}
