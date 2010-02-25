
package com.trilead.ssh2.util;

/**
 * Tokenizer. Why? Because StringTokenizer is not available in J2ME.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: Tokenizer.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class Tokenizer
{
	/**
	 * Exists because StringTokenizer is not available in J2ME.
	 * Returns an array with at least 1 entry.
	 * 
	 * @param source must be non-null
	 * @param delimiter
	 * @return an array of Strings
	 */
	public static String[] parseTokens(String source, char delimiter)
	{
		int numtoken = 1;

		for (int i = 0; i < source.length(); i++)
		{
			if (source.charAt(i) == delimiter)
				numtoken++;
		}

		String list[] = new String[numtoken];
		int nextfield = 0;

		for (int i = 0; i < numtoken; i++)
		{
			if (nextfield >= source.length())
			{
				list[i] = "";
			}
			else
			{
				int idx = source.indexOf(delimiter, nextfield);
				if (idx == -1)
					idx = source.length();
				list[i] = source.substring(nextfield, idx);
				nextfield = idx + 1;
			}
		}

		return list;
	}
}
