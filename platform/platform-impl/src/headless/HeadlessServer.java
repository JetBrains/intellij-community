// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package headless;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ghostawt.image.GGraphics2D;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class HeadlessServer extends WebSocketServer {


    public static Boolean isEnabled() {
        return System.getProperty("awt.toolkit").equals("ghostawt.GhostToolkit");
    }

    /**
     * Root component receiving all events (frame in our case).
     */
    private Container container;

    private HeadlessServer(int port, Container container) throws UnknownHostException {
        super(new InetSocketAddress(port));

        this.container = container;
    }

//    public HeadlessServer(InetSocketAddress address) {
//        super(address);
//    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected.");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println(conn + " disconnected.");
    }

    private String paintAsString() {
        BufferedImage image = new BufferedImage(
                container.getWidth(),
                container.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        synchronized (GGraphics2D.commands) {
            GGraphics2D.commands.clear();
            container.paint(image.getGraphics());

            JsonArray resultArray = new JsonArray(GGraphics2D.commands.size());

            for (JsonElement jel : GGraphics2D.commands) {
                resultArray.add(jel);
            }

            return resultArray.toString();
        }
    }

    private static int getModifiers(JsonObject commandJson) {
        int modifiers = 0; //1040
        if (commandJson.has("shift"))
            modifiers |= InputEvent.SHIFT_MASK;

        if (commandJson.has("ctrl"))
            modifiers |= InputEvent.CTRL_MASK;

        if (commandJson.has("alt"))
            modifiers |= InputEvent.ALT_MASK;

        if (commandJson.has("meta"))
            modifiers |= InputEvent.META_MASK;
        return modifiers;
    }

    //// Eng a
    //401-65, 'a'97, 1
    //400-0,'a'97, 0
    //402-65, 'a'97, 1
    //
    //// Rus ф
    //401- 65, 'ф' 1092,1
    //400- 0, 'ф' 1092,0
    //402- 65, 'ф' 1092,1
    //
    //// F1
    //401, 112, '\uFFFF' 65535, 1
    //402, 112, '\uFFFF' 65535, 1
    //
    //// Enter
    //401, 10, '\n' 10, 1
    //400- 0, '\n' 10,0
    //402, 10, '\n' 10, 1
    private static KeyEvent createKeyEvent(int id, Component source, JsonObject commandJson) {

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component focusedComponent = manager.getFocusOwner();

        if (focusedComponent != null) {
            source = focusedComponent;
        }

        String character = commandJson.get("char").getAsString();
        int keyCode = commandJson.get("keyCode").getAsInt();

        char keyChar;
        if (character.length() > 1) {
            switch (character) {
                case "Tab":
                    keyChar = KeyEvent.VK_TAB;
                    break;
                case "Enter":
                    keyChar = KeyEvent.VK_ENTER;
                    keyCode = 10;
                    break;
                case "Backspace":
                    keyChar = KeyEvent.VK_BACK_SPACE;
                    break;

                default:
                    keyChar = (char) keyCode;
            }
        } else {
            keyChar = character.charAt(0);
        }

        int modifiers = getModifiers(commandJson);

        return new KeyEvent(source, id,
                System.currentTimeMillis(),
                modifiers,
                id == KeyEvent.KEY_TYPED ? 0 : keyCode,
                keyChar,
                id == KeyEvent.KEY_TYPED ? 0 : KeyEvent.KEY_LOCATION_STANDARD);
    }

    // id - MouseEvent.MOUSE_PRESSED e.t.c
    private static MouseEvent createMouseEvent(int id, Component source, JsonObject commandJson) {
        int x = commandJson.get("x").getAsInt();
        int y = commandJson.get("y").getAsInt();

        int button = commandJson.get("button").getAsInt() + 1;

        int modifiers = getModifiers(commandJson);

        //InputEvent.SHIFT_MASK, InputEvent.CTRL_MASK, InputEvent.META_MASK, InputEvent.ALT_MASK
        return new MouseEvent(source,
                id,
                System.currentTimeMillis(),
                modifiers, x, y, 1, false, button);
    }

    private static MouseWheelEvent createMouseWheelEvent(Component source, JsonObject commandJson) {
        int x = commandJson.get("x").getAsInt();
        int y = commandJson.get("y").getAsInt();

        int wheelDelta = commandJson.get("wheelDelta").getAsInt() / 100;

        int modifiers = getModifiers(commandJson);


        //(Component source, int id, long when, int modifiers,
        //int x, int y, int xAbs, int yAbs, int clickCount, boolean popupTrigger,
        // int scrollType, int scrollAmount, int wheelRotation, double preciseWheelRotation) {

        //InputEvent.SHIFT_MASK, InputEvent.CTRL_MASK, InputEvent.META_MASK, InputEvent.ALT_MASK
        return new MouseWheelEvent(source, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers,
                x, y,
                0, 0, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, wheelDelta, wheelDelta>0?-1:1, wheelDelta>0?-1:1);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {

        JsonObject commandJson = new JsonParser().parse(message).getAsJsonObject();
        String command = commandJson.get("command").getAsString();

        switch (command) {
            case "resize": {

                int width = commandJson.get("width").getAsInt();
                int height = commandJson.get("height").getAsInt();

                container.setSize(width, height);

                SwingUtilities.invokeLater(() -> container.revalidate());

            }
            break;

            case "mouseMove": {
                MouseEvent mouseEvent = createMouseEvent(MouseEvent.MOUSE_MOVED, container, commandJson);
                SwingUtilities.invokeLater(() ->  container.dispatchEvent(mouseEvent));
            }
            break;

            case "mouseDown": {
                MouseEvent mouseEvent = createMouseEvent(MouseEvent.MOUSE_PRESSED, container, commandJson);

                SwingUtilities.invokeLater(() -> container.dispatchEvent(mouseEvent));

                // Looks like hack, we are finding and manually settings currently focused component.
                Component focusedComponent = container.findComponentAt(mouseEvent.getPoint());
                if (focusedComponent != null && focusedComponent.isFocusable()) {
                    FocusEvent focusEvent = new FocusEvent(focusedComponent, FocusEvent.FOCUS_FIRST, false);
                    KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                    SwingUtilities.invokeLater(() ->   manager.dispatchEvent(focusEvent));
                }
            }
            break;

            case "mouseUp": {
                MouseEvent mouseEvent = createMouseEvent(MouseEvent.MOUSE_RELEASED, container, commandJson);
                SwingUtilities.invokeLater(() ->  container.dispatchEvent(mouseEvent));
            }
            break;

            case "mouseOut": {
                MouseEvent mouseEvent = createMouseEvent(MouseEvent.MOUSE_EXITED, container, commandJson);
                SwingUtilities.invokeLater(() -> container.dispatchEvent(mouseEvent));
            }
            break;

            case "mouseWheel": {
                MouseWheelEvent mouseWheelEvent = createMouseWheelEvent(container, commandJson);
                try {
                    SwingUtilities.invokeLater(() -> container.dispatchEvent(mouseWheelEvent));
                } catch (Exception e) {
                    // ToDo We need to go deeper to fix this NPE at javax.swing.JViewport.setViewPosition(JViewport.java:1097)
                }
            }
            break;

            case "mouseDrag": {
                KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                Component focusedComponent = manager.getFocusOwner();

                Component targetComponent = focusedComponent == null ? container : focusedComponent;

                MouseEvent mouseEvent = createMouseEvent(MouseEvent.MOUSE_DRAGGED, targetComponent, commandJson);
                try {
                    SwingUtilities.invokeLater(() ->  targetComponent.dispatchEvent(mouseEvent));
                } catch (Exception e) {
                    // ToDo We need to go deeper to fix this NPE at javax.swing.JViewport.setViewPosition(JViewport.java:1097)
                }

            }
            break;

            case "keyDown": {
                KeyEvent keyEvent = createKeyEvent(KeyEvent.KEY_PRESSED, container, commandJson);
                SwingUtilities.invokeLater(() -> container.dispatchEvent(keyEvent));

            }
            break;

            case "keyUp": {
                KeyEvent keyEvent = createKeyEvent(KeyEvent.KEY_RELEASED, container, commandJson);
                SwingUtilities.invokeLater(() -> container.dispatchEvent(keyEvent));
            }
            break;

            case "keyPress": {
                KeyEvent keyEvent = createKeyEvent(KeyEvent.KEY_TYPED, container, commandJson);
                SwingUtilities.invokeLater(() -> container.dispatchEvent(keyEvent));

            }
            break;

            default:
                throw new RuntimeException("Unknown command " + message);
        }

        SwingUtilities.invokeLater(() ->   conn.send(paintAsString()));

    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {

        System.out.println(conn + ": " + message);

        throw new RuntimeException("Unsupported message type " + message);
    }

    public static void startServer(Container container) throws IOException {
        int port = 8887;

        HeadlessServer s = new HeadlessServer(port, container);
        s.start();
        System.out.println("HeadlessServer started on port: " + s.getPort());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();

        // conn.send / close
    }

    @Override
    public void onStart() {
        System.out.println("Server started");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);

        System.out.println("You CANNOT open url like 'http://localhost:8887' directly IN BROWSER!!!");
        System.out.println("Please, as client use index.html in webClient folder.");
        System.out.println("If you want to change server address you can modify it in Application.js");
    }
}