/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.ui.status;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.status.HgChangesetStatus;
import org.zmlx.hg4idea.status.HgCurrentBranchStatus;
import org.zmlx.hg4idea.status.HgRemoteStatusUpdater;

import java.awt.event.MouseEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class HgStatusWidget
  extends EditorBasedWidget
  implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe, HgUpdater {

	private final HgVcs vcs;
	private final HgProjectSettings projectSettings;

	private volatile String myText = "";
  private volatile String myTooltip = "";

  private final HgCurrentBranchStatus hgCurrentBranchStatus = new HgCurrentBranchStatus( this );
  private final HgChangesetStatus incomingChangesStatus;
  private final HgChangesetStatus outgoingChangesStatus;
	private ScheduledFuture<?> changesUpdaterScheduledFuture;

  private static final String myMaxString = "hg: default; in: 99; out: 99";

	private MessageBusConnection busConnection;

	private HgRemoteStatusUpdater remoteUpdater;


	public HgStatusWidget( HgVcs vcs, Project project, HgProjectSettings projectSettings) {
	  super( project );
	  this.vcs = vcs;
	  this.projectSettings = projectSettings;

	  this.incomingChangesStatus = new HgChangesetStatus( "In" );
    this.outgoingChangesStatus = new HgChangesetStatus( "Out" );
  }

  @Override
  public StatusBarWidget copy() {
    return new HgStatusWidget( vcs, getProject(), projectSettings );
  }

  @NotNull
  @Override
  public String ID() {
    return HgStatusWidget.class.getName();
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    update();
  }

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    update();
  }

  @Override
  public void fileClosed(FileEditorManager source, VirtualFile file) {
    update();
  }

  //@Override
  //public void repositoryChanged() {
  //  update();
  //}

  @Override
  public ListPopup getPopupStep() {
    Project project = getProject();
    if (project == null) {
      return null;
    }

    /*
    TODO:
    GitRepository repo = GitBranchUiUtil.getCurrentRepository(project);
    if (repo == null) {
      return null;
    }
    return GitBranchPopup.getInstance(project, repo).asListPopup();
    */

    return null;
  }

  @Override
  public String getSelectedValue() {
    final String text = myText;
    return StringUtil.isEmpty(text) ? "" : "Hg: " + text;
  }

  @NotNull
  @Override
  public String getMaxValue() {
    // todo: ????
    return myMaxString;
  }

  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Override
  // Updates branch information on click
  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
        update();
      }
    };
  }


	@Override
	public void update( final Project project ) {

		ApplicationManager.getApplication().invokeLater(new Runnable() {
	    @Override
	    public void run() {
	      if (project == null) {
	        emptyTextAndTooltip();
	        return;
	      }

		    emptyTextAndTooltip();

		    if ( null != hgCurrentBranchStatus.getStatusText() ) {
			    myText = hgCurrentBranchStatus.getStatusText();
		      myTooltip = hgCurrentBranchStatus.getToolTipText();
		    }

	      if ( incomingChangesStatus.getNumChanges() > 0 ) {
		      myText += "; " + incomingChangesStatus.getStatusName() + ": " + incomingChangesStatus.getNumChanges();
	        myTooltip = "\n" + incomingChangesStatus.getToolTip();
	      }

	      if ( outgoingChangesStatus.getNumChanges() > 0 ) {
		      myText += "; " + outgoingChangesStatus.getStatusName() + ": " + outgoingChangesStatus.getNumChanges();
	        myTooltip += "\n" + outgoingChangesStatus.getToolTip();
	      }

	      int maxLength = myMaxString.length() - 1; // -1, because there are arrows indicating that it is a popup
	      myText = StringUtil.shortenTextWithEllipsis(myText, maxLength, 5);

	      myStatusBar.updateWidget(ID());
	    }
	  });
	}


	public void activate() {

		busConnection = getProject().getMessageBus().connect();
	  busConnection.subscribe( HgVcs.BRANCH_TOPIC, new HgCurrentBranchStatusUpdater( hgCurrentBranchStatus ) );

		remoteUpdater = new HgRemoteStatusUpdater( this, vcs, incomingChangesStatus, outgoingChangesStatus, projectSettings );
		int checkIntervalSeconds = HgGlobalSettings.getIncomingCheckIntervalSeconds();
		changesUpdaterScheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(
			new Runnable() {
				public void run() {
					remoteUpdater.update( getProject() );
				}
			}, 5, checkIntervalSeconds, TimeUnit.SECONDS );

		StatusBar statusBar = WindowManager.getInstance().getStatusBar( getProject() );
		if ( null != statusBar  ) {
			statusBar.addWidget( this );
		}
	}

	public void deactivate() {

		busConnection.disconnect();

		if ( changesUpdaterScheduledFuture != null ) {
			changesUpdaterScheduledFuture.cancel( true );
		}

		StatusBar statusBar = WindowManager.getInstance().getStatusBar( getProject() );
		if ( null != statusBar ) {
			statusBar.removeWidget( ID() );
		}

	}

  private void update() {
	  update( getProject() );
  }

  private void emptyTextAndTooltip() {
    myText = "";
    myTooltip = "";
  }


}  // End of HgStatusWidget class
- 
