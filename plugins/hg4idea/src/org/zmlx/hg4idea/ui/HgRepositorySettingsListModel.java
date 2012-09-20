package org.zmlx.hg4idea.ui;

import javax.swing.*;
import javax.swing.event.ListDataListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * {@link ListModel} for the {@code HgRepositorySettings} list in the {@link HgPushDialog2}.
 *
 */
final class HgRepositorySettingsListModel extends AbstractListModel{
  private final List<HgPushDialog2.HgRepositorySettings> settingsList;

  HgRepositorySettingsListModel(List<HgPushDialog2.HgRepositorySettings> settingsList) {
    this.settingsList = settingsList;
    //fire events when the relevant properties of the list are changed
    final PropertyChangeListener modelUpdater = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        String propertyName = propertyChangeEvent.getPropertyName();
        if ( "valid".equals(propertyName) ||
             "selected".equals(propertyName) ){
          int index = HgRepositorySettingsListModel.this.settingsList.indexOf(propertyChangeEvent.getSource());
          fireContentsChanged(propertyChangeEvent.getSource(), index, index );
        }
      }
    };
    for (HgPushDialog2.HgRepositorySettings repositorySettings : settingsList) {
      repositorySettings.addPropertyChangeListener(modelUpdater);
    }
  }

  @Override
  public int getSize() {
    return settingsList.size();
  }

  @Override
  public Object getElementAt(int i) {
    return settingsList.get( i );
  }
}
