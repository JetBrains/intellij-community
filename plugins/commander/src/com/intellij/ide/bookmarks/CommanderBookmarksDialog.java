package com.intellij.ide.bookmarks;

import com.intellij.ide.commander.Commander;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;

public class CommanderBookmarksDialog extends BookmarksDialog {
  private Project myProject;

  private CommanderBookmarksDialog(BookmarkManager bookmarkManager) {
    super(bookmarkManager);
    myProject = bookmarkManager.getProject();
    setHorizontalStretch(0.75f);
    init();
  }

  protected void gotoSelectedBookmark(boolean closeWindow) {

    CommanderBookmark bookmark = (CommanderBookmark)getSelectedBookmark();
    final PsiElement element = bookmark.getPsiElement();

    ToolWindowManager windowManager=ToolWindowManager.getInstance(myProject);
    windowManager.getToolWindow(ToolWindowId.COMMANDER).activate(
      new Runnable(){
        public void run(){
          Commander.getInstance(myProject).enterElementInActivePanel(element);
        }
      }
    );

    if (closeWindow){
      close(CANCEL_EXIT_CODE);
    }
  }

  public static void execute(BookmarkManager manager, Bookmark currentBookmark) {
    BookmarksDialog dialog = new CommanderBookmarksDialog(manager);
    dialog.setTitle(IdeBundle.message("title.project.elements.bookmarks"));
    dialog.fillList(manager.getValidCommanderBookmarks(), currentBookmark);
    dialog.show();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.bookmarks.CommanderBookmarksDialog";
  }
}
