package com.siyeh.igtest.resources;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.ServerSocket;

public class JNDIResourceInspection {
    public void foo() throws NamingException {
        new InitialContext();
    }

    public void foo2() throws NamingException {
        InitialContext context = new InitialContext();
    }

    public void foo25() throws NamingException {
        try {
            InitialContext context = new InitialContext();
        } finally {
        }

    }


    public void foo3() throws NamingException {
        InitialContext context = new InitialContext();
        context.close();
    }

    public void foo4() throws NamingException {
        InitialContext context = null;
        try {
            context = new InitialContext();
        } catch (NamingException e) {
            e.printStackTrace();
        }
        context.close();
    }

    public void foo5() throws NamingException {
        InitialContext context = null;
        try {
            context = new InitialContext();
        } finally {
            context.close();
        }
    }

    public void foo7() throws NamingException {
        InitialContext context = null;
        try {
            context = new InitialContext();
            context.list("foo");
        } finally {
            context.close();
        }
    }

    public void foo8() throws NamingException {
        InitialContext context = null;
        NamingEnumeration enumeration = null;
        try {
            context = new InitialContext();
            enumeration = context.list("foo");
        } finally {
            enumeration.close();
            context.close();
        }
    }

}
