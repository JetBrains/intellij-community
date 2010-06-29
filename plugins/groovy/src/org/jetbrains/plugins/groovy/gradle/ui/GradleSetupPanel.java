/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gradle.ui;

import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 <!=========================================================================>
 This panel is shown when gradle has not been configured or when an error
 occurs trying to locate the gradle installation. It was intended to be a place
 where the user could specify a gradle installation. Now, it appears to just
 be a placeholder as well as a place to show error message.


 @author mhunsicker
 <!==========================================================================> */
public class GradleSetupPanel
{
   private JPanel mainPanel;
   private JPanel messagePanel;
   private JLabel messageLabel;

   private String messageDetails;
   private String message;
   private JButton detailsButton;


   public GradleSetupPanel()
   {
      setupUI();
   }

   public Component getComponent() { return mainPanel; }

   private void setupUI()
   {
      mainPanel = new JPanel( new BorderLayout() );

      JPanel innerPanel = new JPanel( );
      innerPanel.setLayout( new BoxLayout( innerPanel, BoxLayout.Y_AXIS ) );

      innerPanel.add( createMessageComponent() );

      mainPanel.add( innerPanel, BorderLayout.NORTH );   //this pushes things up. Glue doesn't work in this situation.

      mainPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 10, 10 ) );
   }

   private Component createMessageComponent()
   {
      messagePanel = new JPanel();
      messagePanel.setLayout( new BoxLayout( messagePanel, BoxLayout.X_AXIS ) );

      messageLabel = new JLabel();

      detailsButton = new JButton( new AbstractAction( "Details...")
      {
         public void actionPerformed( ActionEvent e )
         {
            showDetails();
         }
      });

      messagePanel.add( messageLabel );
      messagePanel.add( Box.createHorizontalStrut( 10 ) );
      messagePanel.add( detailsButton );

      messagePanel.setVisible( false );   //hidden by default.

      return messagePanel;
   }

   private void showDetails()
   {
      JTextArea detailsTextArea = new JTextArea( );

      detailsTextArea.setOpaque( true );
      detailsTextArea.setEditable( false );
      detailsTextArea.setBorder( null );

      detailsTextArea.setLineWrap( false );
      detailsTextArea.setWrapStyleWord( true );

      detailsTextArea.setText( message + "\n" + messageDetails );
      detailsTextArea.setCaretPosition( 0 ); //put the caret at the front.

      JBScrollPane scrollPane = new JBScrollPane( detailsTextArea );
      scrollPane.setPreferredSize( new Dimension( 400, 500 ) );

      JOptionPane.showMessageDialog( mainPanel, scrollPane );
   }

   public void setMessage( String message, String messageDetails )
   {
      this.messageDetails = messageDetails;
      this.message = message;

      messageLabel.setText( message );

      detailsButton.setVisible( messageDetails != null );   //only show the 'details' button if there are details

      messagePanel.setVisible( true );
   }

   public void hideMessage()
   {
      messagePanel.setVisible( false );
   }
}
