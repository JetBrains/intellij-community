package com.siyeh.ipp.interfacetoclass;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

// make me readonly
public class ConvertInterfaceToClassImpl extends AbstractAction
        implements ConvertInterfaceToClass
{

    public void actionPerformed(ActionEvent e)
    {
    }
}