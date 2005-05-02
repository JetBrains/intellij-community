package com.siyeh.igtest.resources;

import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;

public class ChannelResourceInspection {

    public void foo2() throws IOException {
        ServerSocket serverSocket = null;
        try{
             serverSocket = new ServerSocket(1, 1);
             serverSocket.getChannel();
        } finally{
            serverSocket.close();
        }
    }
    public void foo3() throws IOException {
        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(1, 1);
            ServerSocketChannel channel = serverSocket.getChannel();
        } finally{
            serverSocket.close();
        }
    }
    public void foo4() throws IOException {
        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(1, 1);
            ServerSocketChannel channel = serverSocket.getChannel();
            channel.close();
        } finally{
            serverSocket.close();
        }
    }

    public void foo5() throws IOException {
        ServerSocket serverSocket = null;
        ServerSocketChannel channel = null;
        try{
            serverSocket = new ServerSocket(1, 1);
            channel = serverSocket.getChannel();
        } finally{
            channel.close();
            serverSocket.close();
        }
    }

}
