# Agent Workbench — Landing Site

Marketing/landing page for the [Agent Workbench](https://plugins.jetbrains.com/plugin/30926-agent-workbench)
IntelliJ plugin. Static site built with Vite + React + TypeScript.

## Develop

```bash
npm install
npm run dev      # start the dev server (prints a localhost URL)
npm run build    # type-check + production build into dist/
npm run preview  # serve the production build locally
```

No backend, no runtime dependencies — `dist/` is fully static and can be hosted anywhere
(e.g. GitHub Pages).

## Content

Feature copy and links live in [`src/data.ts`](src/data.ts) and mirror the plugin descriptor
(`../plugin/resources/META-INF/plugin.xml`). Update copy there, not in the components.

## Screenshots

`public/screenshots/*.svg` are **labeled placeholders** ("screenshot pending"), one per feature.
The file name matches each feature's `slug` in `src/data.ts`.

To drop in a real screenshot, replace the matching file in `public/screenshots/` keeping the same
base name (an `.svg` or a `.png` — if you switch to PNG, update the `slug`/extension wiring in
`src/components/FeatureCard.tsx`). No code change is needed when keeping the `.svg` name.

## Icons

`public/icons/` holds the site logo and per-provider marks. `logo.svg` is the plugin icon; the
Claude mark is a site-local approximation, the others are derived from the plugin's provider icons.
