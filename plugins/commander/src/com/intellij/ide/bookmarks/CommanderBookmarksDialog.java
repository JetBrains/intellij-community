package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.commander.Commander;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;

public class CommanderBookmarksDialog extends BookmarksDialog {
  private final Project myProject;

  private CommanderBookmarksDialog(BookmarkManager bookmarkManager) {
    super(bookmarkManager);
    myProject = bookmarkManager.getProject();
    setHorizontalStretch(0.75f);
    init();
  }

  protected void gotoSelectedBookmark(boolean closeWindow) {

    Bookmark bookmark = getSelectedBookmark();

    VirtualFile file = bookmark.getFile();
    if (file == null) return;

    PsiManager psiManager = PsiManager.getInstance(myProject);

    final PsiElement element = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);

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
    dialog.fillList(manager.getValidBookmarks(), currentBookmark);
    dialog.show();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.bookmarks.CommanderBookmarksDialog";
  }
}
