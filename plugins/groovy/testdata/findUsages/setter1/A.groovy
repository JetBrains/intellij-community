import javax.swing.*
import groovy.swing.SwingBuilder

SwingBuilder builder = new SwingBuilder()

builder.button(name : "Click me")

JButton button = new JButton()

button.<caret>name = "Drag me"