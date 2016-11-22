
package com.trilead.ssh2_build213.sftp;

/**
 * 
 * Attribute Flags. The 'valid-attribute-flags' field in
 * the SFTP ATTRS data type specifies which of the fields are actually present.
 *
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: AttribFlags.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 *
 */
public class AttribFlags
{
	/**
	 * Indicates that the 'allocation-size' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_SIZE = 0x00000001;

	/** Protocol version 6:
	 * 0x00000002 was used in a previous version of this protocol.
	 * It is now a reserved value and MUST NOT appear in the mask.
	 * Some future version of this protocol may reuse this value.
	 */
	public static final int SSH_FILEXFER_ATTR_V3_UIDGID = 0x00000002;

	/**
	 * Indicates that the 'permissions' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_PERMISSIONS = 0x00000004;

	/**
	 * Indicates that the 'atime' and 'mtime' field are present
	 * (protocol v3).
	 */
	public static final int SSH_FILEXFER_ATTR_V3_ACMODTIME = 0x00000008;

	/**
	 * Indicates that the 'atime' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_ACCESSTIME = 0x00000008;

	/**
	 * Indicates that the 'createtime' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_CREATETIME = 0x00000010;

	/**
	 * Indicates that the 'mtime' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_MODIFYTIME = 0x00000020;

	/**
	 * Indicates that the 'acl' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_ACL = 0x00000040;

	/**
	 * Indicates that the 'owner' and 'group' fields are present.
	 */
	public static final int SSH_FILEXFER_ATTR_OWNERGROUP = 0x00000080;

	/**
	 * Indicates that additionally to the 'atime', 'createtime',
	 * 'mtime' and 'ctime' fields (if present), there is also
	 * 'atime-nseconds', 'createtime-nseconds',  'mtime-nseconds' 
	 * and 'ctime-nseconds'.
	 */
	public static final int SSH_FILEXFER_ATTR_SUBSECOND_TIMES = 0x00000100;

	/**
	 * Indicates that the 'attrib-bits' and 'attrib-bits-valid'
	 * fields are present.
	 */
	public static final int SSH_FILEXFER_ATTR_BITS = 0x00000200;

	/**
	 * Indicates that the 'allocation-size' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_ALLOCATION_SIZE = 0x00000400;

	/**
	 * Indicates that the 'text-hint' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_TEXT_HINT = 0x00000800;

	/**
	 * Indicates that the 'mime-type' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_MIME_TYPE = 0x00001000;

	/**
	 * Indicates that the 'link-count' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_LINK_COUNT = 0x00002000;

	/**
	 * Indicates that the 'untranslated-name' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_UNTRANSLATED_NAME = 0x00004000;

	/**
	 * Indicates that the 'ctime' field is present.
	 */
	public static final int SSH_FILEXFER_ATTR_CTIME = 0x00008000;

	/**
	 * Indicates that the 'extended-count' field (and probablby some
	 * 'extensions') is present.
	 */
	public static final int SSH_FILEXFER_ATTR_EXTENDED = 0x80000000;
}
