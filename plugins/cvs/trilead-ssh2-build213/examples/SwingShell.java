import com.trilead.ssh2_build213.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This is a very primitive SSH-2 dumb terminal (Swing based).
 * <p>
 * The purpose of this class is to demonstrate:
 * <ul>
 * <li>Verifying server hostkeys with an existing known_hosts file</li>
 * <li>Displaying fingerprints of server hostkeys</li>
 * <li>Adding a server hostkey to a known_hosts file (+hashing the hostname for security)</li>
 * <li>Authentication with DSA, RSA, password and keyboard-interactive methods</li>
 * </ul>
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SwingShell.java,v 1.10 2007/10/15 12:49:57 cplattne Exp $
 * 
 */
public class SwingShell
{
	/* 
	 * NOTE: to get this feature to work, replace the "tilde" with your home directory,
	 * at least my JVM does not understand it. Need to check the specs.
	 */

	static final String knownHostPath = "~/.ssh/known_hosts";
	static final String idDSAPath = "~/.ssh/id_dsa";
	static final String idRSAPath = "~/.ssh/id_rsa";

	JFrame loginFrame = null;
	JLabel hostLabel;
	JLabel userLabel;
	JTextField hostField;
	JTextField userField;
	JButton loginButton;

	KnownHosts database = new KnownHosts();

	public SwingShell()
	{
		File knownHostFile = new File(knownHostPath);
		if (knownHostFile.exists())
		{
			try
			{
				database.addHostkeys(knownHostFile);
			}
			catch (IOException e)
			{
			}
		}
	}

	/**
	 * This dialog displays a number of text lines and a text field.
	 * The text field can either be plain text or a password field.
	 */
	class EnterSomethingDialog extends JDialog
	{
		private static final long serialVersionUID = 1L;

		JTextField answerField;
		JPasswordField passwordField;

		final boolean isPassword;

		String answer;

		public EnterSomethingDialog(JFrame parent, String title, String content, boolean isPassword)
		{
			this(parent, title, new String[] { content }, isPassword);
		}

		public EnterSomethingDialog(JFrame parent, String title, String[] content, boolean isPassword)
		{
			super(parent, title, true);

			this.isPassword = isPassword;

			JPanel pan = new JPanel();
			pan.setLayout(new BoxLayout(pan, BoxLayout.Y_AXIS));

			for (int i = 0; i < content.length; i++)
			{
				if ((content[i] == null) || (content[i] == ""))
					continue;
				JLabel contentLabel = new JLabel(content[i]);
				pan.add(contentLabel);

			}

			answerField = new JTextField(20);
			passwordField = new JPasswordField(20);

			if (isPassword)
				pan.add(passwordField);
			else
				pan.add(answerField);

			KeyAdapter kl = new KeyAdapter()
			{
				public void keyTyped(KeyEvent e)
				{
					if (e.getKeyChar() == '\n')
						finish();
				}
			};

			answerField.addKeyListener(kl);
			passwordField.addKeyListener(kl);

			getContentPane().add(BorderLayout.CENTER, pan);

			setResizable(false);
			pack();
			setLocationRelativeTo(null);
		}

		private void finish()
		{
			if (isPassword)
				answer = new String(passwordField.getPassword());
			else
				answer = answerField.getText();

			dispose();
		}
	}

	/**
	 * TerminalDialog is probably the worst terminal emulator ever written - implementing
	 * a real vt100 is left as an exercise to the reader, i.e., to you =)
	 *
	 */
	class TerminalDialog extends JDialog
	{
		private static final long serialVersionUID = 1L;

		JPanel botPanel;
		JButton logoffButton;
		JTextArea terminalArea;

		Session sess;
		InputStream in;
		OutputStream out;

		int x, y;

		/**
		 * This thread consumes output from the remote server and displays it in
		 * the terminal window.
		 *
		 */
		class RemoteConsumer extends Thread
		{
			char[][] lines = new char[y][];
			int posy = 0;
			int posx = 0;

			private void addText(byte[] data, int len)
			{
				for (int i = 0; i < len; i++)
				{
					char c = (char) (data[i] & 0xff);

					if (c == 8) // Backspace, VERASE
					{
						if (posx < 0)
							continue;
						posx--;
						continue;
					}

					if (c == '\r')
					{
						posx = 0;
						continue;
					}

					if (c == '\n')
					{
						posy++;
						if (posy >= y)
						{
							for (int k = 1; k < y; k++)
								lines[k - 1] = lines[k];
							posy--;
							lines[y - 1] = new char[x];
							for (int k = 0; k < x; k++)
								lines[y - 1][k] = ' ';
						}
						continue;
					}

					if (c < 32)
					{
						continue;
					}

					if (posx >= x)
					{
						posx = 0;
						posy++;
						if (posy >= y)
						{
							posy--;
							for (int k = 1; k < y; k++)
								lines[k - 1] = lines[k];
							lines[y - 1] = new char[x];
							for (int k = 0; k < x; k++)
								lines[y - 1][k] = ' ';
						}
					}

					if (lines[posy] == null)
					{
						lines[posy] = new char[x];
						for (int k = 0; k < x; k++)
							lines[posy][k] = ' ';
					}

					lines[posy][posx] = c;
					posx++;
				}

				StringBuffer sb = new StringBuffer(x * y);

				for (int i = 0; i < lines.length; i++)
				{
					if (i != 0)
						sb.append('\n');

					if (lines[i] != null)
					{
						sb.append(lines[i]);
					}

				}
				setContent(sb.toString());
			}

			public void run()
			{
				byte[] buff = new byte[8192];

				try
				{
					while (true)
					{
						int len = in.read(buff);
						if (len == -1)
							return;
						addText(buff, len);
					}
				}
				catch (Exception e)
				{
				}
			}
		}

		public TerminalDialog(JFrame parent, String title, Session sess, int x, int y) throws IOException
		{
			super(parent, title, true);

			this.sess = sess;

			in = sess.getStdout();
			out = sess.getStdin();

			this.x = x;
			this.y = y;

			botPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

			logoffButton = new JButton("Logout");
			botPanel.add(logoffButton);

			logoffButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					/* Dispose the dialog, "setVisible(true)" method will return */
					dispose();
				}
			});

			Font f = new Font("Monospaced", Font.PLAIN, 16);

			terminalArea = new JTextArea(y, x);
			terminalArea.setFont(f);
			terminalArea.setBackground(Color.BLACK);
			terminalArea.setForeground(Color.ORANGE);
			/* This is a hack. We cannot disable the caret,
			 * since setting editable to false also changes
			 * the meaning of the TAB key - and I want to use it in bash.
			 * Again - this is a simple DEMO terminal =)
			 */
			terminalArea.setCaretColor(Color.BLACK);

			KeyAdapter kl = new KeyAdapter()
			{
				public void keyTyped(KeyEvent e)
				{
					int c = e.getKeyChar();

					try
					{
						out.write(c);
					}
					catch (IOException e1)
					{
					}
					e.consume();
				}
			};

			terminalArea.addKeyListener(kl);

			getContentPane().add(terminalArea, BorderLayout.CENTER);
			getContentPane().add(botPanel, BorderLayout.PAGE_END);

			setResizable(false);
			pack();
			setLocationRelativeTo(parent);

			new RemoteConsumer().start();
		}

		public void setContent(String lines)
		{
			// setText is thread safe, it does not have to be called from
			// the Swing GUI thread.
			terminalArea.setText(lines);
		}
	}

	/**
	 * This ServerHostKeyVerifier asks the user on how to proceed if a key cannot be found
	 * in the in-memory database.
	 *
	 */
	class AdvancedVerifier implements ServerHostKeyVerifier
	{
		public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm,
				byte[] serverHostKey) throws Exception
		{
			final String host = hostname;
			final String algo = serverHostKeyAlgorithm;

			String message;

			/* Check database */

			int result = database.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey);

			switch (result)
			{
			case KnownHosts.HOSTKEY_IS_OK:
				return true;

			case KnownHosts.HOSTKEY_IS_NEW:
				message = "Do you want to accept the hostkey (type " + algo + ") from " + host + " ?\n";
				break;

			case KnownHosts.HOSTKEY_HAS_CHANGED:
				message = "WARNING! Hostkey for " + host + " has changed!\nAccept anyway?\n";
				break;

			default:
				throw new IllegalStateException();
			}

			/* Include the fingerprints in the message */

			String hexFingerprint = KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey);
			String bubblebabbleFingerprint = KnownHosts.createBubblebabbleFingerprint(serverHostKeyAlgorithm,
					serverHostKey);

			message += "Hex Fingerprint: " + hexFingerprint + "\nBubblebabble Fingerprint: " + bubblebabbleFingerprint;

			/* Now ask the user */

			int choice = JOptionPane.showConfirmDialog(loginFrame, message);

			if (choice == JOptionPane.YES_OPTION)
			{
				/* Be really paranoid. We use a hashed hostname entry */

				String hashedHostname = KnownHosts.createHashedHostname(hostname);

				/* Add the hostkey to the in-memory database */

				database.addHostkey(new String[] { hashedHostname }, serverHostKeyAlgorithm, serverHostKey);

				/* Also try to add the key to a known_host file */

				try
				{
					KnownHosts.addHostkeyToFile(new File(knownHostPath), new String[] { hashedHostname },
							serverHostKeyAlgorithm, serverHostKey);
				}
				catch (IOException ignore)
				{
				}

				return true;
			}

			if (choice == JOptionPane.CANCEL_OPTION)
			{
				throw new Exception("The user aborted the server hostkey verification.");
			}

			return false;
		}
	}

	/**
	 * The logic that one has to implement if "keyboard-interactive" autentication shall be
	 * supported.
	 *
	 */
	class InteractiveLogic implements InteractiveCallback
	{
		int promptCount = 0;
		String lastError;

		public InteractiveLogic(String lastError)
		{
			this.lastError = lastError;
		}

		/* the callback may be invoked several times, depending on how many questions-sets the server sends */

		public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt,
				boolean[] echo) throws IOException
		{
			String[] result = new String[numPrompts];

			for (int i = 0; i < numPrompts; i++)
			{
				/* Often, servers just send empty strings for "name" and "instruction" */

				String[] content = new String[] { lastError, name, instruction, prompt[i] };

				if (lastError != null)
				{
					/* show lastError only once */
					lastError = null;
				}

				EnterSomethingDialog esd = new EnterSomethingDialog(loginFrame, "Keyboard Interactive Authentication",
						content, !echo[i]);

				esd.setVisible(true);

				if (esd.answer == null)
					throw new IOException("Login aborted by user");

				result[i] = esd.answer;
				promptCount++;
			}

			return result;
		}

		/* We maintain a prompt counter - this enables the detection of situations where the ssh
		 * server is signaling "authentication failed" even though it did not send a single prompt.
		 */

		public int getPromptCount()
		{
			return promptCount;
		}
	}

	/**
	 * The SSH-2 connection is established in this thread.
	 * If we would not use a separate thread (e.g., put this code in
	 * the event handler of the "Login" button) then the GUI would not
	 * be responsive (missing window repaints if you move the window etc.)
	 */
	class ConnectionThread extends Thread
	{
		String hostname;
		String username;

		public ConnectionThread(String hostname, String username)
		{
			this.hostname = hostname;
			this.username = username;
		}

		public void run()
		{
			Connection conn = new Connection(hostname);

			try
			{
				/*
				 * 
				 * CONNECT AND VERIFY SERVER HOST KEY (with callback)
				 * 
				 */

				String[] hostkeyAlgos = database.getPreferredServerHostkeyAlgorithmOrder(hostname);

				if (hostkeyAlgos != null)
					conn.setServerHostKeyAlgorithms(hostkeyAlgos);

				conn.connect(new AdvancedVerifier());

				/*
				 * 
				 * AUTHENTICATION PHASE
				 * 
				 */

				boolean enableKeyboardInteractive = true;
				boolean enableDSA = true;
				boolean enableRSA = true;

				String lastError = null;

				while (true)
				{
					if ((enableDSA || enableRSA) && conn.isAuthMethodAvailable(username, "publickey"))
					{
						if (enableDSA)
						{
							File key = new File(idDSAPath);

							if (key.exists())
							{
								EnterSomethingDialog esd = new EnterSomethingDialog(loginFrame, "DSA Authentication",
										new String[] { lastError, "Enter DSA private key password:" }, true);
								esd.setVisible(true);

								boolean res = conn.authenticateWithPublicKey(username, key, esd.answer);

								if (res == true)
									break;

								lastError = "DSA authentication failed.";
							}
							enableDSA = false; // do not try again
						}

						if (enableRSA)
						{
							File key = new File(idRSAPath);

							if (key.exists())
							{
								EnterSomethingDialog esd = new EnterSomethingDialog(loginFrame, "RSA Authentication",
										new String[] { lastError, "Enter RSA private key password:" }, true);
								esd.setVisible(true);

								boolean res = conn.authenticateWithPublicKey(username, key, esd.answer);

								if (res == true)
									break;

								lastError = "RSA authentication failed.";
							}
							enableRSA = false; // do not try again
						}

						continue;
					}

					if (enableKeyboardInteractive && conn.isAuthMethodAvailable(username, "keyboard-interactive"))
					{
						InteractiveLogic il = new InteractiveLogic(lastError);

						boolean res = conn.authenticateWithKeyboardInteractive(username, il);

						if (res == true)
							break;

						if (il.getPromptCount() == 0)
						{
							// aha. the server announced that it supports "keyboard-interactive", but when
							// we asked for it, it just denied the request without sending us any prompt.
							// That happens with some server versions/configurations.
							// We just disable the "keyboard-interactive" method and notify the user.

							lastError = "Keyboard-interactive does not work.";

							enableKeyboardInteractive = false; // do not try this again
						}
						else
						{
							lastError = "Keyboard-interactive auth failed."; // try again, if possible
						}

						continue;
					}

					if (conn.isAuthMethodAvailable(username, "password"))
					{
						final EnterSomethingDialog esd = new EnterSomethingDialog(loginFrame,
								"Password Authentication",
								new String[] { lastError, "Enter password for " + username }, true);

						esd.setVisible(true);

						if (esd.answer == null)
							throw new IOException("Login aborted by user");

						boolean res = conn.authenticateWithPassword(username, esd.answer);

						if (res == true)
							break;

						lastError = "Password authentication failed."; // try again, if possible

						continue;
					}

					throw new IOException("No supported authentication methods available.");
				}

				/*
				 * 
				 * AUTHENTICATION OK. DO SOMETHING.
				 * 
				 */

				Session sess = conn.openSession();

				int x_width = 90;
				int y_width = 30;

				sess.requestPTY("dumb", x_width, y_width, 0, 0, null);
				sess.startShell();

				TerminalDialog td = new TerminalDialog(loginFrame, username + "@" + hostname, sess, x_width, y_width);

				/* The following call blocks until the dialog has been closed */

				td.setVisible(true);

			}
			catch (IOException e)
			{
				//e.printStackTrace();
				JOptionPane.showMessageDialog(loginFrame, "Exception: " + e.getMessage());
			}

			/*
			 * 
			 * CLOSE THE CONNECTION.
			 * 
			 */

			conn.close();

			/*
			 * 
			 * CLOSE THE LOGIN FRAME - APPLICATION WILL BE EXITED (no more frames)
			 * 
			 */

			Runnable r = new Runnable()
			{
				public void run()
				{
					loginFrame.dispose();
				}
			};

			SwingUtilities.invokeLater(r);
		}
	}

	void loginPressed()
	{
		String hostname = hostField.getText().trim();
		String username = userField.getText().trim();

		if ((hostname.length() == 0) || (username.length() == 0))
		{
			JOptionPane.showMessageDialog(loginFrame, "Please fill out both fields!");
			return;
		}

		loginButton.setEnabled(false);
		hostField.setEnabled(false);
		userField.setEnabled(false);

		ConnectionThread ct = new ConnectionThread(hostname, username);

		ct.start();
	}

	void showGUI()
	{
		loginFrame = new JFrame("Trilead SSH-2 for Java SwingShell");

		hostLabel = new JLabel("Hostname:");
		userLabel = new JLabel("Username:");

		hostField = new JTextField("", 20);
		userField = new JTextField("", 10);

		loginButton = new JButton("Login");

		loginButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				loginPressed();
			}
		});

		JPanel loginPanel = new JPanel();

		loginPanel.add(hostLabel);
		loginPanel.add(hostField);
		loginPanel.add(userLabel);
		loginPanel.add(userField);
		loginPanel.add(loginButton);

		loginFrame.getRootPane().setDefaultButton(loginButton);

		loginFrame.getContentPane().add(loginPanel, BorderLayout.PAGE_START);
		//loginFrame.getContentPane().add(textArea, BorderLayout.CENTER);

		loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		loginFrame.pack();
		loginFrame.setResizable(false);
		loginFrame.setLocationRelativeTo(null);
		loginFrame.setVisible(true);
	}

	void startGUI()
	{
		Runnable r = new Runnable()
		{
			public void run()
			{
				showGUI();
			}
		};

		SwingUtilities.invokeLater(r);

	}

	public static void main(String[] args)
	{
		SwingShell client = new SwingShell();
		client.startGUI();
	}
}
