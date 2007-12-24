package com.maddyhome.idea.copyright.options;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Icons;
import org.jdom.Document;
import org.jdom.Element;

import java.awt.Window;
import java.io.File;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;

public class ExternalOptionHelper
{
    public static Options getExternalOptions(Project project)
    {
        JFileChooser chooser = createChooser();
        Window window = WindowManager.getInstance().suggestParentWindow(project);
        if (chooser.showDialog(window, "Import") != JFileChooser.APPROVE_OPTION)
        {
            return null;
        }

        File file = chooser.getSelectedFile();

        return loadOptions(file);
    }

    private static Options loadOptions(File file)
    {
        try
        {
            Document doc = JDOMUtil.loadDocument(file);
            Element root = doc.getRootElement();
            List list = root.getChildren("component");
            for (Object element : list)
            {
                Element component = (Element)element;
                String name = component.getAttributeValue("name");
                if (name.equals("copyright"))
                {
                    Options res = new Options();
                    res.readExternal(component);

                    return res;
                }
            }

            return null;
        }
        catch (Exception e)
        {
            logger.error(e);
            return null;
        }
    }

    private static JFileChooser createChooser()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Copyright Settings");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(false);

        chooser.setFileView(new FileView() {
            public Icon getIcon(File file)
            {
                if (file.isFile() &&
                    (file.getName().toLowerCase().endsWith(".ipr") ||
                    file.getName().toLowerCase().endsWith(".iml")))
                {
                    return Icons.PROJECT_ICON;
                }
                else
                {
                    return super.getIcon(file);
                }
            }
        });

        chooser.setFileFilter(new FileFilter() {
            public boolean accept(File file)
            {
                return file.isDirectory() ||
                    file.getName().toLowerCase().endsWith(".ipr") ||
                    file.getName().toLowerCase().endsWith(".iml");
            }

            public String getDescription()
            {
                return "Project/Module files (*.ipr,*.iml)";
            }
        });

        return chooser;
    }

    private ExternalOptionHelper()
    {
    }

    private static final Logger logger = Logger.getInstance(ExternalOptionHelper.class.getName());
}
