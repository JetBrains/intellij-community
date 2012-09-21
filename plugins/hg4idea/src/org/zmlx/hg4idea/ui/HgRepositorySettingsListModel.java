// Copyright Robin Stevens
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * {@link ListModel} for the {@code HgRepositorySettings} list in the {@link HgPushDialog}.
 *
 */
final class HgRepositorySettingsListModel extends AbstractListModel{
  private final List<HgPushDialog.HgRepositorySettings> mySettingsList;

  HgRepositorySettingsListModel(List<HgPushDialog.HgRepositorySettings> settingsList) {
    this.mySettingsList = settingsList;
    //fire events when the relevant properties of the list are changed
    final PropertyChangeListener modelUpdater = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        String propertyName = propertyChangeEvent.getPropertyName();
        if ( "valid".equals(propertyName) ||
             "selected".equals(propertyName) ){
          int index = HgRepositorySettingsListModel.this.mySettingsList.indexOf(propertyChangeEvent.getSource());
          fireContentsChanged(propertyChangeEvent.getSource(), index, index );
        }
      }
    };
    for (HgPushDialog.HgRepositorySettings repositorySettings : settingsList) {
      repositorySettings.addPropertyChangeListener(modelUpdater);
    }
  }

  @Override
  public int getSize() {
    return mySettingsList.size();
  }

  @Override
  public Object getElementAt(int i) {
    return mySettingsList.get( i );
  }
}
