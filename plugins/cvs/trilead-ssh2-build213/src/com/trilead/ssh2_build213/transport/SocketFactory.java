package com.trilead.ssh2_build213.transport;

import com.trilead.ssh2_build213.HTTPProxyData;
import com.trilead.ssh2_build213.HTTPProxyException;
import com.trilead.ssh2_build213.ProxyData;
import com.trilead.ssh2_build213.SelfConnectionProxyData;
import com.trilead.ssh2_build213.crypto.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketFactory {
    public static Socket open(final String hostname, final int port, final ProxyData proxyData, final int connectTimeout) throws IOException {
        final Socket sock = new Socket();
        if (proxyData == null)
        {
            InetAddress addr = TransportManager.createInetAddress(hostname);
            sock.connect(new InetSocketAddress(addr, port), connectTimeout);
            sock.setSoTimeout(0);
            return sock;
        }

        if (proxyData instanceof SelfConnectionProxyData) {
            // already connected
            return ((SelfConnectionProxyData) proxyData).connect();
        }

        if (proxyData instanceof HTTPProxyData)
        {
            HTTPProxyData pd = (HTTPProxyData) proxyData;

            /* At the moment, we only support HTTP proxies */

            InetAddress addr = TransportManager.createInetAddress(pd.proxyHost);
            sock.connect(new InetSocketAddress(addr, pd.proxyPort), connectTimeout);
            sock.setSoTimeout(0);

            /* OK, now tell the proxy where we actually want to connect to */

            StringBuffer sb = new StringBuffer();

            sb.append("CONNECT ");
            sb.append(hostname);
            sb.append(':');
            sb.append(port);
            sb.append(" HTTP/1.0\r\n");

            if ((pd.proxyUser != null) && (pd.proxyPass != null))
            {
                String credentials = pd.proxyUser + ":" + pd.proxyPass;
                char[] encoded = Base64.encode(credentials.getBytes("ISO-8859-1"));
                sb.append("Proxy-Authorization: Basic ");
                sb.append(encoded);
                sb.append("\r\n");
            }

            if (pd.requestHeaderLines != null)
            {
                for (int i = 0; i < pd.requestHeaderLines.length; i++)
                {
                    if (pd.requestHeaderLines[i] != null)
                    {
                        sb.append(pd.requestHeaderLines[i]);
                        sb.append("\r\n");
                    }
                }
            }

            sb.append("\r\n");

            OutputStream out = sock.getOutputStream();

            out.write(sb.toString().getBytes("ISO-8859-1"));
            out.flush();

            /* Now parse the HTTP response */

            byte[] buffer = new byte[1024];
            InputStream in = sock.getInputStream();

            int len = ClientServerHello.readLineRN(in, buffer);

            String httpReponse = new String(buffer, 0, len, "ISO-8859-1");

            if (httpReponse.startsWith("HTTP/") == false)
                throw new IOException("The proxy did not send back a valid HTTP response.");

            /* "HTTP/1.X XYZ X" => 14 characters minimum */

            if ((httpReponse.length() < 14) || (httpReponse.charAt(8) != ' ') || (httpReponse.charAt(12) != ' '))
                throw new IOException("The proxy did not send back a valid HTTP response.");

            int errorCode = 0;

            try
            {
                errorCode = Integer.parseInt(httpReponse.substring(9, 12));
            }
            catch (NumberFormatException ignore)
            {
                throw new IOException("The proxy did not send back a valid HTTP response.");
            }

            if ((errorCode < 0) || (errorCode > 999))
                throw new IOException("The proxy did not send back a valid HTTP response.");

            if (errorCode != 200)
            {
                throw new HTTPProxyException(httpReponse.substring(13), errorCode);
            }

            /* OK, read until empty line */

            while (true)
            {
                len = ClientServerHello.readLineRN(in, buffer);
                if (len == 0)
                    break;
            }
            return sock;
        }

        throw new IOException("Unsupported ProxyData");
    }
}
