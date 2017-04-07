import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.WindowConstants;
import java.awt.Toolkit;
import java.io.IOException;

/**
 * Created by Tom on 27/04/2015.
 */
public class ImageEditor extends AnAction {
    MainForm f;
    AnActionEvent event;

    public void actionPerformed(AnActionEvent e) {
        event = e;
        f = new MainForm(this);
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Toolkit t = Toolkit.getDefaultToolkit();
        int x = (int) ((t.getScreenSize().getWidth() - (double) f.getWidth()) / 2.0D);
        int y = (int) ((t.getScreenSize().getHeight() - (double) f.getHeight()) / 2.0D);
        f.setLocation(x, y);
        //checkIfImageIsOpen(e);
        f.setVisible(true);
    }

//    private void checkIfImageIsOpen(AnActionEvent event) {
//        final Project project = event.getProject();
//        if(project == null){
//            return;
//        }
//        VirtualFile[] files = FileEditorManager.getInstance(project).getOpenFiles();
//        for(VirtualFile file : files){
//            String filename = file.getName().substring(file.getName().length() - 3,file.getName().length());
//            System.out.println(filename);
//            if(filename.equals("jpg") || filename.equals("png")){
//                try {
//                    byte[] bytes = file.contentsToByteArray();
//                    f.openImage(bytes);
//                    return;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
}
