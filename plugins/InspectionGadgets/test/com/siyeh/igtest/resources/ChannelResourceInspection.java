package com.siyeh.igtest.resources;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.FileChannel;

public class ChannelResourceInspection {

    public void foo2() throws IOException {
        ServerSocket serverSocket = new ServerSocket(1, 1);
        serverSocket.getChannel();
        try {

        } finally {
            serverSocket.close();
        }
    }

    public void foo3() throws IOException {
        ServerSocket serverSocket = null;
        serverSocket = new ServerSocket(1, 1);
        ServerSocketChannel channel = serverSocket.getChannel();
        try {
        } finally {
            serverSocket.close();
        }
    }

    public void foo4() throws IOException {
        ServerSocket serverSocket = null;
        serverSocket = new ServerSocket(1, 1);
        ServerSocketChannel channel = serverSocket.getChannel();
        try {
            channel.close();
        } finally {
            serverSocket.close();
        }
    }

    public void foo5() throws IOException {
        ServerSocket serverSocket = null;
        ServerSocketChannel channel = null;
        serverSocket = new ServerSocket(1, 1);
        channel = serverSocket.getChannel();
        try {
            // do stuff
        } finally {
            channel.close();
            serverSocket.close();
        }
    }

    public static void copyFile(@NotNull File from, @NotNull File to)
            throws IOException {
        if (from.getCanonicalPath().equals(to.getCanonicalPath()))
            return;
        final FileInputStream inStream = new FileInputStream(from);
        try {
            boolean success = false;
            try {
                final FileOutputStream outStream = new FileOutputStream(to);
                try {
                    final FileChannel inChannel = inStream.getChannel();
                    try {
                        final FileChannel outChannel = outStream.getChannel();
                        try {
                            inChannel.transferTo(0, inChannel.size(), outChannel);
                        }
                        finally {
                            outChannel.close();
                        }
                    }
                    finally {
                        inChannel.close();
                    }
                }
                finally {
                    outStream.close();
                }
                //copyLastModifiedDate( from, to );
                success = true;
            }
            finally {
                if (!success)
                    to.delete();
            }
        }
        finally {
            inStream.close();
        }
    }

    public static void copy(FileInputStream source,
                            FileOutputStream target)
            throws IOException {
        FileChannel sourceChannel = source.getChannel(); // line 4
        FileChannel targetChannel = target.getChannel(); // line 5
        try {
            sourceChannel.transferTo(0, sourceChannel.size(),
                    targetChannel);
        }
        finally {
            try {
                targetChannel.close();
            }
            finally {
                sourceChannel.close();
            }
        }
    }

    public static void copyFile2(File source, File target)
            throws IOException {
        final FileInputStream sourceStream = new FileInputStream(source);
        try {
            final FileOutputStream targetStream =
                    new FileOutputStream(target);
            try {
                copy(sourceStream, targetStream);
            }
            finally {
                targetStream.close();
            }
        }
        finally {
            sourceStream.close();
        }
    }
}
