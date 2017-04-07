import com.google.common.io.ByteStreams;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.IOUtil;
import org.apache.sanselan.util.IOUtils;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Thomas Needham on Mon Apr 27 17:21:39 BST 2015
 * @author Thomas Needham
 */

public class MainForm extends JFrame {
    private JPanel rootPanel;
    private JMenuBar menuStrip;
    private JMenu fileMenu;
    private JMenuItem openImageItem;
    private JMenuItem closeImageItem;
    private JMenuItem saveImageItem;
    private JMenuItem saveAsItem;
    private JMenuItem exitItem;
    private JMenu aboutMenu;
    private JMenuItem aboutThisPluginItem;
    private JMenu editMenu;
    private JMenuItem resizeItem;
    private JLabel imagePane;
    private ImageEditor editor;
    private String openfile;

    public MainForm(ImageEditor editor) {
        this.editor = editor;
        this.setTitle("Lightweight Image Editor");
        initComponents();
    }

    private void initComponents() {
        createUI();
        addEventListeners();
    }

    private void addEventListeners() {

    }

    private void createUI() {
        rootPanel = new JPanel();
        menuStrip = new JMenuBar();
        fileMenu = new JMenu();
        openImageItem = new JMenuItem();
        closeImageItem = new JMenuItem();
        saveImageItem = new JMenuItem();
        exitItem = new JMenuItem();
        aboutMenu = new JMenu();
        editMenu = new JMenu();
        resizeItem = new JMenuItem();
        aboutThisPluginItem = new JMenuItem();
        imagePane = new JLabel();

        //======== this ========
        setLayout(new BorderLayout());

        //======== rootPanel ========
        {
            rootPanel.setLayout(null);

            //======== menuStrip ========
            {

                //======== fileMenu ========
                {
                    fileMenu.setText("File");

                    //---- openImageItem ----
                    openImageItem.setText("Open Image");
                    openImageItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            OpenFileDialog();
                        }
                    });
                    fileMenu.add(openImageItem);

                    //---- closeImageItem ----
                    closeImageItem.setText("Close Image");
                    closeImageItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            SaveBeforeClose(false);
                        }
                    });
                    fileMenu.add(closeImageItem);

                    //---- saveImageItem ----
                    saveImageItem.setText("Save Image");
                    saveImageItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            SaveFileDialog();
                        }
                    });
                    fileMenu.add(saveImageItem);

                    //---- exitItem ----
                    exitItem.setText("Exit");
                    exitItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            SaveBeforeClose(true);
                        }
                    });
                    fileMenu.add(exitItem);
                }
                menuStrip.add(fileMenu);

                //======== editMemu ========

                {
                    editMenu.setText("Edit");

                    //---- resizeItem ----
                    resizeItem.setText("Resize Image");
                    resizeItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            ResizeImage();
                        }
                    });
                    editMenu.add(resizeItem);
                }
                menuStrip.add(editMenu);
                //======== aboutMenu ========
                {
                    aboutMenu.setText("About");

                    //---- aboutThisPluginItem ----
                    aboutThisPluginItem.setText("About This Plugin");
                    aboutThisPluginItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            DisplayAbout();
                        }
                    });
                    aboutMenu.add(aboutThisPluginItem);
                }
                menuStrip.add(aboutMenu);
            }
            rootPanel.add(menuStrip);
            menuStrip.setBounds(0, 0, 395, menuStrip.getPreferredSize().height);
            rootPanel.add(imagePane);
            imagePane.setBounds(5, 30, 390, 230);

            { // compute preferred size
                Dimension preferredSize = new Dimension();
                for (int i = 0; i < rootPanel.getComponentCount(); i++) {
                    Rectangle bounds = rootPanel.getComponent(i).getBounds();
                    preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                    preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                }
                Insets insets = rootPanel.getInsets();
                preferredSize.width += insets.right;
                preferredSize.height += insets.bottom;
                rootPanel.setMinimumSize(preferredSize);
                rootPanel.setPreferredSize(preferredSize);
            }
        }
        add(rootPanel, BorderLayout.CENTER);
        this.pack();
    }

    private void ResizeImage() {
        int width;
        int height;
        if(imagePane.getIcon() == null){
            return;
        }
        try {

            width = Integer.parseInt(JOptionPane.showInputDialog(this, "Enter image width"));
            height = Integer.parseInt(JOptionPane.showInputDialog(this, "Enter image Height"));
        }
        catch (NumberFormatException ex){
            ex.printStackTrace();
            return;
        }
        ImageIcon icon = (ImageIcon) imagePane.getIcon();
        BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics g = bi.createGraphics();
        // paint the Icon to the BufferedImage.
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        Image image = bi.getScaledInstance(width,height,Image.SCALE_DEFAULT);
        ImageIcon newicon = new ImageIcon(image);
        imagePane.setIcon(newicon);


    }

    private void DisplayAbout() {
        JOptionPane.showConfirmDialog(this,"Lightweight image editor plugin for intellij" + "\n" + "Copyright Thomas Needham 2015","About this plugin",JOptionPane.DEFAULT_OPTION);
    }

    private void SaveBeforeClose(boolean exiting) {
        if(imagePane.getIcon() == null){
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,"Do you want to save your changes?","Save Changes",JOptionPane.YES_NO_CANCEL_OPTION);
        if(result == JOptionPane.YES_OPTION){
            SaveFileDialog();
        }
        else if(result == JOptionPane.CANCEL_OPTION){
            return;
        }

        imagePane.setText("");
        imagePane.setIcon(null);
        openfile = "";
        if(exiting){
            this.dispose();
        }

    }

    private void SaveFileDialog() {
        Project proj = editor.event.getProject();
        if(proj != null){
            final JFileChooser jfc = new JFileChooser(proj.getBasePath());
            jfc.addChoosableFileFilter(new ImageFileFilter());
            int returnval = jfc.showSaveDialog(this);
            if(returnval == JFileChooser.APPROVE_OPTION){
                SaveFile(jfc.getSelectedFile());
            }
        }
    }

    private void SaveFile(File f) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(f,false);
            String filetype = openfile.substring(openfile.length() - 3, openfile.length());
            ImageIcon icon = (ImageIcon) imagePane.getIcon();
            BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics g = bi.createGraphics();
        // paint the Icon to the BufferedImage.
            icon.paintIcon(null, g, 0,0);
            ImageIO.write(bi, filetype, fileOutputStream);
            g.dispose();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void OpenFileDialog() {

        Project proj = editor.event.getProject();
        if(proj != null){
            final JFileChooser jfc = new JFileChooser(proj.getBasePath());
            jfc.addChoosableFileFilter(new ImageFileFilter());
            int returnval = jfc.showOpenDialog(this);
           if(returnval == JFileChooser.APPROVE_OPTION){
               ReadFrle(jfc.getSelectedFile());
           }
        }
    }

    private void ReadFrle(File f) {
        FileInputStream inputStream;
        if(f == null){
            return;
        }
        openfile = f.getAbsolutePath();
        try {
            inputStream = new FileInputStream(f);
            byte[] bytes = new byte[(int)f.length()];
            inputStream.read(bytes);
            imagePane.setIcon(new ImageIcon(bytes));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean openImage(byte[] bytes){
        imagePane.setIcon(new ImageIcon(bytes));
        this.repaint();
        return true;
    }

}
