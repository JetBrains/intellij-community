package com.siyeh.igtest.resources;

import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.ServerSocket;

public class SocketResourceInspection {
    public void foo() throws IOException, UnknownHostException {
       new Socket( InetAddress.getLocalHost(), 1);
    }

    public void foo2() throws IOException, UnknownHostException {
        final Socket socket = new Socket(InetAddress.getLocalHost(), 1);
    }

    public void foo25() throws IOException, UnknownHostException {
        try {
            final Socket socket = new Socket(InetAddress.getLocalHost(), 1);
        } finally {
        }

    }


    public void foo3() throws IOException {
        final Socket socket = new Socket(InetAddress.getLocalHost(), 1);
        socket.close();
    }

    public void foo4() throws IOException {
        Socket socket = null;
        try {
            socket = new Socket(InetAddress.getLocalHost(), 1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        socket.close();
    }

    public void foo5() throws IOException {
        Socket socket = null;
        try {
            socket = new Socket(InetAddress.getLocalHost(), 1);
        } finally {
            socket.close();
        }
    }
    public void foo6() throws IOException {
        ServerSocket socket = null;
        try{
            socket = new ServerSocket(1, 1);
        } finally{
        }
    }
    public void foo7() throws IOException {
        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(1, 1);
            Socket socket = serverSocket.accept();
        } finally{
            serverSocket.close();
        }
    }

}
