
package com.trilead.ssh2_build213;

import com.trilead.ssh2_build213.packets.TypesReader;
import com.trilead.ssh2_build213.packets.TypesWriter;
import com.trilead.ssh2_build213.sftp.AttribFlags;
import com.trilead.ssh2_build213.sftp.ErrorCodes;
import com.trilead.ssh2_build213.sftp.Packet;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Vector;


/**
 * A <code>SFTPv3Client</code> represents a SFTP (protocol version 3)
 * client connection tunnelled over a SSH-2 connection. This is a very simple
 * (synchronous) implementation.
 * <p>
 * Basically, most methods in this class map directly to one of
 * the packet types described in draft-ietf-secsh-filexfer-02.txt.
 * <p>
 * Note: this is experimental code.
 * <p>
 * Error handling: the methods of this class throw IOExceptions. However, unless
 * there is catastrophic failure, exceptions of the type {@link SFTPv3Client} will
 * be thrown (a subclass of IOException). Therefore, you can implement more verbose
 * behavior by checking if a thrown exception if of this type. If yes, then you
 * can cast the exception and access detailed information about the failure. 
 * <p>
 * Notes about file names, directory names and paths, copy-pasted
 * from the specs:
 * <ul>
 * <li>SFTP v3 represents file names as strings. File names are
 * assumed to use the slash ('/') character as a directory separator.</li>
 * <li>File names starting with a slash are "absolute", and are relative to
 * the root of the file system.  Names starting with any other character
 * are relative to the user's default directory (home directory).</li>
 * <li>Servers SHOULD interpret a path name component ".." as referring to
 * the parent directory, and "." as referring to the current directory.
 * If the server implementation limits access to certain parts of the
 * file system, it must be extra careful in parsing file names when
 * enforcing such restrictions.  There have been numerous reported
 * security bugs where a ".." in a path name has allowed access outside
 * the intended area.</li>
 * <li>An empty path name is valid, and it refers to the user's default
 * directory (usually the user's home directory).</li>
 * </ul>
 * <p>
 * If you are still not tired then please go on and read the comment for
 * {@link #setCharset(String)}.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SFTPv3Client.java,v 1.3 2008/04/01 12:38:09 cplattne Exp $
 */
public class SFTPv3Client
{
	final Connection conn;
	final Session sess;
	final PrintStream debug;

	boolean flag_closed = false;

	InputStream is;
	OutputStream os;

	int protocol_version = 0;
	HashMap server_extensions = new HashMap();

	int next_request_id = 1000;

	String charsetName = null;

	/**
	 * Create a SFTP v3 client.
	 * 
	 * @param conn The underlying SSH-2 connection to be used.
	 * @param debug
	 * @throws IOException
	 * 
	 * @deprecated this constructor (debug version) will disappear in the future,
	 *             use {@link #SFTPv3Client(Connection)} instead.
	 */
	public SFTPv3Client(Connection conn, PrintStream debug) throws IOException
	{
		if (conn == null)
			throw new IllegalArgumentException("Cannot accept null argument!");

		this.conn = conn;
		this.debug = debug;

		if (debug != null)
			debug.println("Opening session and starting SFTP subsystem.");

		sess = conn.openSession();
		sess.startSubSystem("sftp");

		is = sess.getStdout();
		os = new BufferedOutputStream(sess.getStdin(), 2048);

		if ((is == null) || (os == null))
			throw new IOException("There is a problem with the streams of the underlying channel.");

		init();
	}

	/**
	 * Create a SFTP v3 client.
	 * 
	 * @param conn The underlying SSH-2 connection to be used.
	 * @throws IOException
	 */
	public SFTPv3Client(Connection conn) throws IOException
	{
		this(conn, null);
	}

	/**
	 * Set the charset used to convert between Java Unicode Strings and byte encodings
	 * used by the server for paths and file names. Unfortunately, the SFTP v3 draft
	 * says NOTHING about such conversions (well, with the exception of error messages
	 * which have to be in UTF-8). Newer drafts specify to use UTF-8 for file names
	 * (if I remember correctly). However, a quick test using OpenSSH serving a EXT-3
	 * filesystem has shown that UTF-8 seems to be a bad choice for SFTP v3 (tested with
	 * filenames containing german umlauts). "windows-1252" seems to work better for Europe.
	 * Luckily, "windows-1252" is the platform default in my case =).
	 * <p>
	 * If you don't set anything, then the platform default will be used (this is the default
	 * behavior).
	 * 
	 * @see #getCharset()
	 * 
	 * @param charset the name of the charset to be used or <code>null</code> to use the platform's
	 *        default encoding.
	 * @throws IOException
	 */
	public void setCharset(String charset) throws IOException
	{
		if (charset == null)
		{
			charsetName = charset;
			return;
		}

		try
		{
			Charset.forName(charset);
		}
		catch (Exception e)
		{
			throw (IOException) new IOException("This charset is not supported").initCause(e);
		}
		charsetName = charset;
	}

	/**
	 * The currently used charset for filename encoding/decoding.
	 * 
	 * @see #setCharset(String)
	 * 
	 * @return The name of the charset (<code>null</code> if the platform's default charset is being used)
	 */
	public String getCharset()
	{
		return charsetName;
	}

	private final void checkHandleValidAndOpen(SFTPv3FileHandle handle) throws IOException
	{
		if (handle.client != this)
			throw new IOException("The file handle was created with another SFTPv3FileHandle instance.");

		if (handle.isClosed == true)
			throw new IOException("The file handle is closed.");
	}

	private final void sendMessage(int type, int requestId, byte[] msg, int off, int len) throws IOException
	{
		int msglen = len + 1;

		if (type != Packet.SSH_FXP_INIT)
			msglen += 4;

		os.write(msglen >> 24);
		os.write(msglen >> 16);
		os.write(msglen >> 8);
		os.write(msglen);
		os.write(type);

		if (type != Packet.SSH_FXP_INIT)
		{
			os.write(requestId >> 24);
			os.write(requestId >> 16);
			os.write(requestId >> 8);
			os.write(requestId);
		}

		os.write(msg, off, len);
		os.flush();
	}

	private final void sendMessage(int type, int requestId, byte[] msg) throws IOException
	{
		sendMessage(type, requestId, msg, 0, msg.length);
	}

	private final void readBytes(byte[] buff, int pos, int len) throws IOException
	{
		while (len > 0)
		{
			int count = is.read(buff, pos, len);
			if (count < 0)
				throw new IOException("Unexpected end of sftp stream.");
			if ((count == 0) || (count > len))
				throw new IOException("Underlying stream implementation is bogus!");
			len -= count;
			pos += count;
		}
	}

	/**
	 * Read a message and guarantee that the <b>contents</b> is not larger than
	 * <code>maxlen</code> bytes.
	 * <p>
	 * Note: receiveMessage(34000) actually means that the message may be up to 34004
	 * bytes (the length attribute preceeding the contents is 4 bytes).
	 * 
	 * @param maxlen
	 * @return the message contents
	 * @throws IOException
	 */
	private final byte[] receiveMessage(int maxlen) throws IOException
	{
		byte[] msglen = new byte[4];

		readBytes(msglen, 0, 4);

		int len = (((msglen[0] & 0xff) << 24) | ((msglen[1] & 0xff) << 16) | ((msglen[2] & 0xff) << 8) | (msglen[3] & 0xff));

		if ((len > maxlen) || (len <= 0))
			throw new IOException("Illegal sftp packet len: " + len);

		byte[] msg = new byte[len];

		readBytes(msg, 0, len);

		return msg;
	}

	private final int generateNextRequestID()
	{
		synchronized (this)
		{
			return next_request_id++;
		}
	}

	private final void closeHandle(byte[] handle) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(handle, 0, handle.length);

		sendMessage(Packet.SSH_FXP_CLOSE, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	private SFTPv3FileAttributes readAttrs(TypesReader tr) throws IOException
	{
		/*
		 * uint32   flags
		 * uint64   size           present only if flag SSH_FILEXFER_ATTR_SIZE
		 * uint32   uid            present only if flag SSH_FILEXFER_ATTR_V3_UIDGID
		 * uint32   gid            present only if flag SSH_FILEXFER_ATTR_V3_UIDGID
		 * uint32   permissions    present only if flag SSH_FILEXFER_ATTR_PERMISSIONS
		 * uint32   atime          present only if flag SSH_FILEXFER_ATTR_V3_ACMODTIME
		 * uint32   mtime          present only if flag SSH_FILEXFER_ATTR_V3_ACMODTIME
		 * uint32   extended_count present only if flag SSH_FILEXFER_ATTR_EXTENDED
		 * string   extended_type
		 * string   extended_data
		 * ...      more extended data (extended_type - extended_data pairs),
		 *          so that number of pairs equals extended_count
		 */

		SFTPv3FileAttributes fa = new SFTPv3FileAttributes();

		int flags = tr.readUINT32();

		if ((flags & AttribFlags.SSH_FILEXFER_ATTR_SIZE) != 0)
		{
			if (debug != null)
				debug.println("SSH_FILEXFER_ATTR_SIZE");
			fa.size = new Long(tr.readUINT64());
		}

		if ((flags & AttribFlags.SSH_FILEXFER_ATTR_V3_UIDGID) != 0)
		{
			if (debug != null)
				debug.println("SSH_FILEXFER_ATTR_V3_UIDGID");
			fa.uid = new Integer(tr.readUINT32());
			fa.gid = new Integer(tr.readUINT32());
		}

		if ((flags & AttribFlags.SSH_FILEXFER_ATTR_PERMISSIONS) != 0)
		{
			if (debug != null)
				debug.println("SSH_FILEXFER_ATTR_PERMISSIONS");
			fa.permissions = new Integer(tr.readUINT32());
		}

		if ((flags & AttribFlags.SSH_FILEXFER_ATTR_V3_ACMODTIME) != 0)
		{
			if (debug != null)
				debug.println("SSH_FILEXFER_ATTR_V3_ACMODTIME");
			fa.atime = new Long(((long)tr.readUINT32()) & 0xffffffffl);
			fa.mtime = new Long(((long)tr.readUINT32()) & 0xffffffffl);

		}

		if ((flags & AttribFlags.SSH_FILEXFER_ATTR_EXTENDED) != 0)
		{
			int count = tr.readUINT32();

			if (debug != null)
				debug.println("SSH_FILEXFER_ATTR_EXTENDED (" + count + ")");

			/* Read it anyway to detect corrupt packets */

			while (count > 0)
			{
				tr.readByteString();
				tr.readByteString();
				count--;
			}
		}

		return fa;
	}

	/**
	 * Retrieve the file attributes of an open file.
	 * 
	 * @param handle a SFTPv3FileHandle handle.
	 * @return a SFTPv3FileAttributes object.
	 * @throws IOException
	 */
	public SFTPv3FileAttributes fstat(SFTPv3FileHandle handle) throws IOException
	{
		checkHandleValidAndOpen(handle);

		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(handle.fileHandle, 0, handle.fileHandle.length);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_FSTAT...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_FSTAT, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		if (debug != null)
		{
			debug.println("Got REPLY.");
			debug.flush();
		}

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_ATTRS)
		{
			return readAttrs(tr);
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();

		throw new SFTPException(tr.readString(), errorCode);
	}

	private SFTPv3FileAttributes statBoth(String path, int statMethod) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(path, charsetName);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_STAT/SSH_FXP_LSTAT...");
			debug.flush();
		}

		sendMessage(statMethod, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		if (debug != null)
		{
			debug.println("Got REPLY.");
			debug.flush();
		}

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_ATTRS)
		{
			return readAttrs(tr);
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();

		throw new SFTPException(tr.readString(), errorCode);
	}

	/**
	 * Retrieve the file attributes of a file. This method
	 * follows symbolic links on the server.
	 * 
	 * @see #lstat(String)
	 * 
	 * @param path See the {@link SFTPv3Client comment} for the class for more details.
	 * @return a SFTPv3FileAttributes object.
	 * @throws IOException
	 */
	public SFTPv3FileAttributes stat(String path) throws IOException
	{
		return statBoth(path, Packet.SSH_FXP_STAT);
	}

	/**
	 * Retrieve the file attributes of a file. This method
	 * does NOT follow symbolic links on the server.
	 * 
	 * @see #stat(String)
	 * 
	 * @param path See the {@link SFTPv3Client comment} for the class for more details.
	 * @return a SFTPv3FileAttributes object.
	 * @throws IOException
	 */
	public SFTPv3FileAttributes lstat(String path) throws IOException
	{
		return statBoth(path, Packet.SSH_FXP_LSTAT);
	}

	/**
	 * Read the target of a symbolic link.
	 * 
	 * @param path See the {@link SFTPv3Client comment} for the class for more details.
	 * @return The target of the link.
	 * @throws IOException
	 */
	public String readLink(String path) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(path, charsetName);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_READLINK...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_READLINK, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		if (debug != null)
		{
			debug.println("Got REPLY.");
			debug.flush();
		}

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_NAME)
		{
			int count = tr.readUINT32();

			if (count != 1)
				throw new IOException("The server sent an invalid SSH_FXP_NAME packet.");

			return tr.readString(charsetName);
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();

		throw new SFTPException(tr.readString(), errorCode);
	}

	private void expectStatusOKMessage(int id) throws IOException
	{
		byte[] resp = receiveMessage(34000);

		if (debug != null)
		{
			debug.println("Got REPLY.");
			debug.flush();
		}

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != id)
			throw new IOException("The server sent an invalid id field.");

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();

		if (errorCode == ErrorCodes.SSH_FX_OK)
			return;

		throw new SFTPException(tr.readString(), errorCode);
	}

	/**
	 *  Modify the attributes of a file. Used for operations such as changing
	 *  the ownership, permissions or access times, as well as for truncating a file.
	 * 
	 * @param path See the {@link SFTPv3Client comment} for the class for more details.
	 * @param attr A SFTPv3FileAttributes object. Specifies the modifications to be
	 *             made to the attributes of the file. Empty fields will be ignored.
	 * @throws IOException
	 */
	public void setstat(String path, SFTPv3FileAttributes attr) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(path, charsetName);
		tw.writeBytes(createAttrs(attr));

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_SETSTAT...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_SETSTAT, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * 	Modify the attributes of a file. Used for operations such as changing
	 *  the ownership, permissions or access times, as well as for truncating a file.
	 * 
	 * @param handle a SFTPv3FileHandle handle
	 * @param attr A SFTPv3FileAttributes object. Specifies the modifications to be
	 *             made to the attributes of the file. Empty fields will be ignored.
	 * @throws IOException
	 */
	public void fsetstat(SFTPv3FileHandle handle, SFTPv3FileAttributes attr) throws IOException
	{
		checkHandleValidAndOpen(handle);

		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(handle.fileHandle, 0, handle.fileHandle.length);
		tw.writeBytes(createAttrs(attr));

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_FSETSTAT...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_FSETSTAT, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * Create a symbolic link on the server. Creates a link "src" that points
	 * to "target".
	 * 
	 * @param src See the {@link SFTPv3Client comment} for the class for more details.
	 * @param target See the {@link SFTPv3Client comment} for the class for more details.
	 * @throws IOException
	 */
	public void createSymlink(String src, String target) throws IOException
	{
		int req_id = generateNextRequestID();

		/* Either I am too stupid to understand the SFTP draft
		 * or the OpenSSH guys changed the semantics of src and target.
		 */

		TypesWriter tw = new TypesWriter();
		tw.writeString(target, charsetName);
		tw.writeString(src, charsetName);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_SYMLINK...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_SYMLINK, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * Have the server canonicalize any given path name to an absolute path.
	 * This is useful for converting path names containing ".." components or
	 * relative pathnames without a leading slash into absolute paths.
	 * 
	 * @param path See the {@link SFTPv3Client comment} for the class for more details.
	 * @return An absolute path.
	 * @throws IOException
	 */
	public String canonicalPath(String path) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(path, charsetName);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_REALPATH...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_REALPATH, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		if (debug != null)
		{
			debug.println("Got REPLY.");
			debug.flush();
		}

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_NAME)
		{
			int count = tr.readUINT32();

			if (count != 1)
				throw new IOException("The server sent an invalid SSH_FXP_NAME packet.");

			return tr.readString(charsetName);
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();

		throw new SFTPException(tr.readString(), errorCode);
	}

	private final Vector scanDirectory(byte[] handle) throws IOException
	{
		Vector files = new Vector();

		while (true)
		{
			int req_id = generateNextRequestID();

			TypesWriter tw = new TypesWriter();
			tw.writeString(handle, 0, handle.length);

			if (debug != null)
			{
				debug.println("Sending SSH_FXP_READDIR...");
				debug.flush();
			}

			sendMessage(Packet.SSH_FXP_READDIR, req_id, tw.getBytes());
		
			/* Some servers send here a packet with size > 34000 */
			/* To whom it may concern: please learn to read the specs. */
			
			byte[] resp = receiveMessage(65536);

			if (debug != null)
			{
				debug.println("Got REPLY.");
				debug.flush();
			}

			TypesReader tr = new TypesReader(resp);

			int t = tr.readByte();

			int rep_id = tr.readUINT32();
			if (rep_id != req_id)
				throw new IOException("The server sent an invalid id field.");

			if (t == Packet.SSH_FXP_NAME)
			{
				int count = tr.readUINT32();

				if (debug != null)
					debug.println("Parsing " + count + " name entries...");

				while (count > 0)
				{
					SFTPv3DirectoryEntry dirEnt = new SFTPv3DirectoryEntry();

					dirEnt.filename = tr.readString(charsetName);
					dirEnt.longEntry = tr.readString(charsetName);

					dirEnt.attributes = readAttrs(tr);
					files.addElement(dirEnt);

					if (debug != null)
						debug.println("File: '" + dirEnt.filename + "'");
					count--;
				}
				continue;
			}

			if (t != Packet.SSH_FXP_STATUS)
				throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

			int errorCode = tr.readUINT32();

			if (errorCode == ErrorCodes.SSH_FX_EOF)
				return files;

			throw new SFTPException(tr.readString(), errorCode);
		}
	}

	private final byte[] openDirectory(String path) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(path, charsetName);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_OPENDIR...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_OPENDIR, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_HANDLE)
		{
			if (debug != null)
			{
				debug.println("Got SSH_FXP_HANDLE.");
				debug.flush();
			}

			byte[] handle = tr.readByteString();
			return handle;
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();
		String errorMessage = tr.readString();

		throw new SFTPException(errorMessage, errorCode);
	}

	private final String expandString(byte[] b, int off, int len)
	{
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < len; i++)
		{
			int c = b[off + i] & 0xff;

			if ((c >= 32) && (c <= 126))
			{
				sb.append((char) c);
			}
			else
			{
				sb.append("{0x" + Integer.toHexString(c) + "}");
			}
		}

		return sb.toString();
	}

	private void init() throws IOException
	{
		/* Send SSH_FXP_INIT (version 3) */

		final int client_version = 3;

		if (debug != null)
			debug.println("Sending SSH_FXP_INIT (" + client_version + ")...");

		TypesWriter tw = new TypesWriter();
		tw.writeUINT32(client_version);
		sendMessage(Packet.SSH_FXP_INIT, 0, tw.getBytes());

		/* Receive SSH_FXP_VERSION */

		if (debug != null)
			debug.println("Waiting for SSH_FXP_VERSION...");

		TypesReader tr = new TypesReader(receiveMessage(34000)); /* Should be enough for any reasonable server */

		int type = tr.readByte();

		if (type != Packet.SSH_FXP_VERSION)
		{
			throw new IOException("The server did not send a SSH_FXP_VERSION packet (got " + type + ")");
		}

		protocol_version = tr.readUINT32();

		if (debug != null)
			debug.println("SSH_FXP_VERSION: protocol_version = " + protocol_version);

		if (protocol_version != 3)
			throw new IOException("Server version " + protocol_version + " is currently not supported");

		/* Read and save extensions (if any) for later use */

		while (tr.remain() != 0)
		{
			String name = tr.readString();
			byte[] value = tr.readByteString();
			server_extensions.put(name, value);

			if (debug != null)
				debug.println("SSH_FXP_VERSION: extension: " + name + " = '" + expandString(value, 0, value.length)
						+ "'");
		}
	}

	/**
	 * Returns the negotiated SFTP protocol version between the client and the server.
	 * 
	 * @return SFTP protocol version, i.e., "3".
	 * 
	 */
	public int getProtocolVersion()
	{
		return protocol_version;
	}

	/**
	 * Close this SFTP session. NEVER forget to call this method to free up
	 * resources - even if you got an exception from one of the other methods.
	 * Sometimes these other methods may throw an exception, saying that the
	 * underlying channel is closed (this can happen, e.g., if the other server
	 * sent a close message.) However, as long as you have not called the
	 * <code>close()</code> method, you are likely wasting resources.
	 * 
	 */
	public void close()
	{
		sess.close();
	}

	/**
	 * List the contents of a directory.
	 * 
	 * @param dirName See the {@link SFTPv3Client comment} for the class for more details.
	 * @return A Vector containing {@link SFTPv3DirectoryEntry} objects.
	 * @throws IOException
	 */
	public Vector ls(String dirName) throws IOException
	{
		byte[] handle = openDirectory(dirName);
		Vector result = scanDirectory(handle);
		closeHandle(handle);
		return result;
	}

	/**
	 * Create a new directory.
	 * 
	 * @param dirName See the {@link SFTPv3Client comment} for the class for more details.
	 * @param posixPermissions the permissions for this directory, e.g., "0700" (remember that
	 *                         this is octal noation). The server will likely apply a umask.
	 * 
	 * @throws IOException
	 */
	public void mkdir(String dirName, int posixPermissions) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(dirName, charsetName);
		tw.writeUINT32(AttribFlags.SSH_FILEXFER_ATTR_PERMISSIONS);
		tw.writeUINT32(posixPermissions);

		sendMessage(Packet.SSH_FXP_MKDIR, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * Remove a file.
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @throws IOException
	 */
	public void rm(String fileName) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(fileName, charsetName);

		sendMessage(Packet.SSH_FXP_REMOVE, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * Remove an empty directory. 
	 * 
	 * @param dirName See the {@link SFTPv3Client comment} for the class for more details.
	 * @throws IOException
	 */
	public void rmdir(String dirName) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(dirName, charsetName);

		sendMessage(Packet.SSH_FXP_RMDIR, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * Move a file or directory.
	 * 
	 * @param oldPath See the {@link SFTPv3Client comment} for the class for more details.
	 * @param newPath See the {@link SFTPv3Client comment} for the class for more details.
	 * @throws IOException
	 */
	public void mv(String oldPath, String newPath) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(oldPath, charsetName);
		tw.writeString(newPath, charsetName);

		sendMessage(Packet.SSH_FXP_RENAME, req_id, tw.getBytes());

		expectStatusOKMessage(req_id);
	}

	/**
	 * Open a file for reading.
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @return a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public SFTPv3FileHandle openFileRO(String fileName) throws IOException
	{
		return openFile(fileName, 0x00000001, null); // SSH_FXF_READ	
	}

	/**
	 * Open a file for reading and writing.
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @return a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public SFTPv3FileHandle openFileRW(String fileName) throws IOException
	{
		return openFile(fileName, 0x00000003, null); // SSH_FXF_READ | SSH_FXF_WRITE
	}

	// Append is broken (already in the specification, because there is no way to
	// send a write operation (what offset to use??))
	//	public SFTPv3FileHandle openFileRWAppend(String fileName) throws IOException
	//	{
	//		return openFile(fileName, 0x00000007, null); // SSH_FXF_READ | SSH_FXF_WRITE | SSH_FXF_APPEND
	//	}

	/**
	 * Create a file and open it for reading and writing.
	 * Same as {@link #createFile(String, SFTPv3FileAttributes) createFile(fileName, null)}.
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @return a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public SFTPv3FileHandle createFile(String fileName) throws IOException
	{
		return createFile(fileName, null);
	}

	/**
	 * Create a file and open it for reading and writing.
	 * You can specify the default attributes of the file (the server may or may
	 * not respect your wishes).
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @param attr may be <code>null</code> to use server defaults. Probably only
	 *             the <code>uid</code>, <code>gid</code> and <code>permissions</code>
	 *             (remember the server may apply a umask) entries of the {@link SFTPv3FileHandle}
	 *             structure make sense. You need only to set those fields where you want
	 *             to override the server's defaults.
	 * @return a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public SFTPv3FileHandle createFile(String fileName, SFTPv3FileAttributes attr) throws IOException
	{
		return openFile(fileName, 0x00000008 | 0x00000003, attr); // SSH_FXF_CREAT | SSH_FXF_READ | SSH_FXF_WRITE
	}

	/**
	 * Create a file (truncate it if it already exists) and open it for reading and writing.
	 * Same as {@link #createFileTruncate(String, SFTPv3FileAttributes) createFileTruncate(fileName, null)}.
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @return a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public SFTPv3FileHandle createFileTruncate(String fileName) throws IOException
	{
		return createFileTruncate(fileName, null);
	}

	/**
	 * reate a file (truncate it if it already exists) and open it for reading and writing.
	 * You can specify the default attributes of the file (the server may or may
	 * not respect your wishes).
	 * 
	 * @param fileName See the {@link SFTPv3Client comment} for the class for more details.
	 * @param attr may be <code>null</code> to use server defaults. Probably only
	 *             the <code>uid</code>, <code>gid</code> and <code>permissions</code>
	 *             (remember the server may apply a umask) entries of the {@link SFTPv3FileHandle}
	 *             structure make sense. You need only to set those fields where you want
	 *             to override the server's defaults.
	 * @return a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public SFTPv3FileHandle createFileTruncate(String fileName, SFTPv3FileAttributes attr) throws IOException
	{
		return openFile(fileName, 0x00000018 | 0x00000003, attr); // SSH_FXF_CREAT | SSH_FXF_TRUNC | SSH_FXF_READ | SSH_FXF_WRITE
	}

	private byte[] createAttrs(SFTPv3FileAttributes attr)
	{
		TypesWriter tw = new TypesWriter();

		int attrFlags = 0;

		if (attr == null)
		{
			tw.writeUINT32(0);
		}
		else
		{
			if (attr.size != null)
				attrFlags = attrFlags | AttribFlags.SSH_FILEXFER_ATTR_SIZE;

			if ((attr.uid != null) && (attr.gid != null))
				attrFlags = attrFlags | AttribFlags.SSH_FILEXFER_ATTR_V3_UIDGID;

			if (attr.permissions != null)
				attrFlags = attrFlags | AttribFlags.SSH_FILEXFER_ATTR_PERMISSIONS;

			if ((attr.atime != null) && (attr.mtime != null))
				attrFlags = attrFlags | AttribFlags.SSH_FILEXFER_ATTR_V3_ACMODTIME;

			tw.writeUINT32(attrFlags);

			if (attr.size != null)
				tw.writeUINT64(attr.size.longValue());

			if ((attr.uid != null) && (attr.gid != null))
			{
				tw.writeUINT32(attr.uid.intValue());
				tw.writeUINT32(attr.gid.intValue());
			}

			if (attr.permissions != null)
				tw.writeUINT32(attr.permissions.intValue());

			if ((attr.atime != null) && (attr.mtime != null))
			{
				tw.writeUINT32(attr.atime.intValue());
				tw.writeUINT32(attr.mtime.intValue());
			}
		}

		return tw.getBytes();
	}

	private SFTPv3FileHandle openFile(String fileName, int flags, SFTPv3FileAttributes attr) throws IOException
	{
		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(fileName, charsetName);
		tw.writeUINT32(flags);
		tw.writeBytes(createAttrs(attr));

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_OPEN...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_OPEN, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_HANDLE)
		{
			if (debug != null)
			{
				debug.println("Got SSH_FXP_HANDLE.");
				debug.flush();
			}

			return new SFTPv3FileHandle(this, tr.readByteString());
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();
		String errorMessage = tr.readString();

		throw new SFTPException(errorMessage, errorCode);
	}

	/**
	 * Read bytes from a file. No more than 32768 bytes may be read at once.
	 * Be aware that the semantics of read() are different than for Java streams.
	 * <p>
	 * <ul>
	 * <li>The server will read as many bytes as it can from the file (up to <code>len</code>),
	 * and return them.</li>
	 * <li>If EOF is encountered before reading any data, <code>-1</code> is returned.
	 * <li>If an error occurs, an exception is thrown</li>.
	 * <li>For normal disk files, it is guaranteed that the server will return the specified
	 * number of bytes, or up to end of file. For, e.g., device files this may return
	 * fewer bytes than requested.</li>
	 * </ul>
	 * 
	 * @param handle a SFTPv3FileHandle handle
	 * @param fileOffset offset (in bytes) in the file
	 * @param dst the destination byte array
	 * @param dstoff offset in the destination byte array
	 * @param len how many bytes to read, 0 &lt; len &lt;= 32768 bytes
	 * @return the number of bytes that could be read, may be less than requested if
	 *         the end of the file is reached, -1 is returned in case of <code>EOF</code>
	 * @throws IOException
	 */
	public int read(SFTPv3FileHandle handle, long fileOffset, byte[] dst, int dstoff, int len) throws IOException
	{
		checkHandleValidAndOpen(handle);

		if ((len > 32768) || (len <= 0))
			throw new IllegalArgumentException("invalid len argument");

		int req_id = generateNextRequestID();

		TypesWriter tw = new TypesWriter();
		tw.writeString(handle.fileHandle, 0, handle.fileHandle.length);
		tw.writeUINT64(fileOffset);
		tw.writeUINT32(len);

		if (debug != null)
		{
			debug.println("Sending SSH_FXP_READ...");
			debug.flush();
		}

		sendMessage(Packet.SSH_FXP_READ, req_id, tw.getBytes());

		byte[] resp = receiveMessage(34000);

		TypesReader tr = new TypesReader(resp);

		int t = tr.readByte();

		int rep_id = tr.readUINT32();
		if (rep_id != req_id)
			throw new IOException("The server sent an invalid id field.");

		if (t == Packet.SSH_FXP_DATA)
		{
			if (debug != null)
			{
				debug.println("Got SSH_FXP_DATA...");
				debug.flush();
			}

			int readLen = tr.readUINT32();

			if ((readLen < 0) || (readLen > len))
				throw new IOException("The server sent an invalid length field.");

			tr.readBytes(dst, dstoff, readLen);

			return readLen;
		}

		if (t != Packet.SSH_FXP_STATUS)
			throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

		int errorCode = tr.readUINT32();

		if (errorCode == ErrorCodes.SSH_FX_EOF)
		{
			if (debug != null)
			{
				debug.println("Got SSH_FX_EOF.");
				debug.flush();
			}

			return -1;
		}

		String errorMessage = tr.readString();

		throw new SFTPException(errorMessage, errorCode);
	}

	/**
	 * Write bytes to a file. If <code>len</code> &gt; 32768, then the write operation will
	 * be split into multiple writes.
	 * 
	 * @param handle a SFTPv3FileHandle handle.
	 * @param fileOffset offset (in bytes) in the file.
	 * @param src the source byte array.
	 * @param srcoff offset in the source byte array.
	 * @param len how many bytes to write.
	 * @throws IOException
	 */
	public void write(SFTPv3FileHandle handle, long fileOffset, byte[] src, int srcoff, int len) throws IOException
	{
		checkHandleValidAndOpen(handle);

		while (len > 0)
		{
			int writeRequestLen = len;

			if (writeRequestLen > 32768)
				writeRequestLen = 32768;

			int req_id = generateNextRequestID();

			TypesWriter tw = new TypesWriter();
			tw.writeString(handle.fileHandle, 0, handle.fileHandle.length);
			tw.writeUINT64(fileOffset);
			tw.writeString(src, srcoff, writeRequestLen);

			if (debug != null)
			{
				debug.println("Sending SSH_FXP_WRITE...");
				debug.flush();
			}

			sendMessage(Packet.SSH_FXP_WRITE, req_id, tw.getBytes());

			fileOffset += writeRequestLen;

			srcoff += writeRequestLen;
			len -= writeRequestLen;

			byte[] resp = receiveMessage(34000);

			TypesReader tr = new TypesReader(resp);

			int t = tr.readByte();

			int rep_id = tr.readUINT32();
			if (rep_id != req_id)
				throw new IOException("The server sent an invalid id field.");

			if (t != Packet.SSH_FXP_STATUS)
				throw new IOException("The SFTP server sent an unexpected packet type (" + t + ")");

			int errorCode = tr.readUINT32();

			if (errorCode == ErrorCodes.SSH_FX_OK)
				continue;

			String errorMessage = tr.readString();

			throw new SFTPException(errorMessage, errorCode);
		}
	}

	/**
	 * Close a file.
	 * 
	 * @param handle a SFTPv3FileHandle handle
	 * @throws IOException
	 */
	public void closeFile(SFTPv3FileHandle handle) throws IOException
	{
		if (handle == null)
			throw new IllegalArgumentException("the handle argument may not be null");

		try
		{
			if (handle.isClosed == false)
			{
				closeHandle(handle.fileHandle);
			}
		}
		finally
		{
			handle.isClosed = true;
		}
	}
}
